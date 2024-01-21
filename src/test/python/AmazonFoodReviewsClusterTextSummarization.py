import os
import math
import numpy as np
import pandas as pd
import tiktoken
from openai import OpenAI

#openai-1.6.1-py3
from ast import literal_eval
import time

#@retry(wait=wait_random_exponential(min=1, max=20), stop=stop_after_attempt(6))
def get_embedding(text: str, model="davinci-003", **kwargs) : #-> List[float]:
    #openai.api_key
    #https://platform.openai.com/docs/quickstart?context=python
    # replace newlines, which can negatively affect performance.
    # for free limits, we have 3 per minute
    #https://cookbook.openai.com/examples/how_to_handle_rate_limits
    delay_in_seconds = 20
    time.sleep(delay_in_seconds)
    text = text.replace("\n", " ")
    response = openai.embeddings.create(input=[text], model=model, **kwargs)
    return response.data[0].embedding

# this follows the openai text summarization examples:
# the openai github repository uses MIT license: https://github.com/openai/openai-cookbook/blob/main/LICENSE
# https://github.com/openai/openai-cookbook/blob/f6b0cb189f680866d0bbecbba3cafb6df9c50a68/examples/Get_embeddings_from_dataset.ipynb
# https://github.com/openai/openai-cookbook/blob/main/examples/Clustering.ipynb

csv_path = os.getcwd() + "/../../test/resources/amazon_fine_food_reviews_cleaned.csv"

df = pd.read_csv(csv_path, index_col=0)

df.columns
# Index(['ProductId', 'UserId', 'ProfileName', 'HelpfulnessNumerator',
#       'HelpfulnessDenominator', 'Score', 'Time', 'Summary', 'Text'],
#      dtype='object')

# if True, create sparse utility matrix for SVD projection
if False:
    # rows are user idxs
    # cols are product idxs
    # values are scores
    indexes = [[], []]
    data = []
    # data_sk = []
    prods_id_to_idx = {}
    users_id_to_idx = {}
    # make reverse dictionaries to look up product and user id strings
    prods_idx_to_id = {}
    users_idx_to_id = {}
    for ii, row in df.iterrows():
        pid = row["ProductId"]
        uid = row["UserId"]
        s = row["Score"]
        if pid in prods_id_to_idx:
            pidx = prods_id_to_idx[pid]
        else:
            pidx = len(prods_id_to_idx)
            prods_id_to_idx[pid] = pidx
        if uid in users_id_to_idx:
            uidx = users_id_to_idx[pid]
        else:
            uidx = len(users_id_to_idx)
            users_id_to_idx[uid] = uidx
        prods_idx_to_id[pidx] = pid
        users_idx_to_id[uidx] = uid
        indexes[0].append(uidx)
        indexes[1].append(pidx)
        score = int(s.rstrip("\n"))
        data.append(score)
        # data_sk.append((uidx, pidx, score))

    print(f"read the indices and data for sparse utility matrix\n")
    # from scipy.sparse import coo_array
    # sparse_utility_2 = coo_array((data, (indexes[0], indexes[1])), shape=(n_users, n_prods))
    ## can convert with .tocsr() etc
    import torch
    indexes = torch.tensor(indexes, )
    data = torch.tensor(data, dtype=torch.float32)
    sparse_utility = torch.sparse_coo_tensor(indexes, data, [n_users, n_prods])
    print(f"have sparse utility matrix\n")
    seed = 12345
    torch.manual_seed(seed)
    u, s, v = torch.svd_lowrank(sparse_utility, q=2, niter=2, M=None)
    print(f"from SVD: u.shape={u.shape}, s.shape={s.shape}, v.shape={v.shape}\n")
    print(f"s={s}\n")

    '''
    # this is how to write the indices and data for a COO format sparse matrix pytorch tensor:
    >>> i = torch.tensor([[0, 1, 1],
                          [2, 0, 2]])
    >>> v = torch.tensor([3, 4, 5], dtype=torch.float32)
    >>> s= torch.sparse_coo_tensor(i, v, [2, 4])
    >>> s.to_dense()
    s.to_dense()
    tensor([[0., 0., 3., 0.],
            [4., 0., 5., 0.]])
    '''
    # projected = sparse_utility * v
    projected = torch.sparse.mm(sparse_utility, v)
    assert (projected.shape[1] == 2)
    print(f"projected.shape={projected.shape}\n")
    p = projected.numpy()

    import matplotlib.pyplot as plt

    plt.scatter(projected[:, 0].numpy(), projected[:, 1].numpy(), s=1)
    plt.show()

    '''
    projections near 0,0 are users who reviewed 1 product, but those products have been reviewed by many users.
    the ratings may be different.  e.g. user ADT0SRK1MGOEU rates product B006K2ZZ7K as a 4 while all other
    reviews of that product are 5.

    projections to the left of center with 2nd coordinate being near 0
    are users with many reviews who have products in common

    projections above center with 1st coordinate being near 0
    in clusters are users with a couple of reviews and those users have a product in common.

    projections to left and above center and not in clusters
    are users with several reviews having no products in common

    projections to left and above center that are in clusters
    are users with a few reviews that have 1 product in common.  a spot check shows that
    all have the same high score for that product in common.

    projections to left and below center that are in clusters
    are users with a few reviews that have 1 product in common.  a spot check shows that
    all have the same high score for that product in common.
    '''

# continue preparing the text + summary embeddings
df = df.dropna()
# combine Text and Summary columns:
df["combined"] = (
    "Title: " + df.Summary.str.strip() + "; Content: " + df.Text.str.strip()
)
df.head(2)

df.shape
#(396400, 10)

# NOTE: for openai embeddings free rate limit, we have 3 per minute.  1000 requests = 5.6 hours
# so for a first quick look, will use top_n = 100 and choose from the data sorted by productId,
# though that leads to a natural clustering already.
# if increase the limit back up to top_n=1000, then should use the sorted by Time option.
# NOTE: after have run the requests, can store them in a csv file and read from that
if True:
    # quick first run
    top_n = 100
    df = df.head(2 * top_n)
else:
    # use 1000 entries
    top_n = 1000
    df = df.sort_values("Time").tail(top_n * 2)  # first cut to first 2k entries, assuming less than half will be fil

df.drop("Time", axis=1, inplace=True)

out_embeddings_path = os.getcwd() + "/../../test/resources/" + \
                      "amazon_fine_food_reviews_cleaned_embeddings_" + str(top_n) + ".csv"

# embedding model parameters
client = OpenAI(
    # This is the default and can be omitted
    api_key=os.environ.get("OPENAI_API_KEY"),
)
embedding_model = "text-embedding-ada-002"
embedding_encoding = "cl100k_base"  # this the encoding for text-embedding-ada-002
max_tokens = 8000  # the maximum for text-embedding-ada-002 is 8191
encoding = tiktoken.get_encoding(embedding_encoding)

# omit reviews that are too long to embed
df["n_tokens"] = df.combined.apply(lambda x: len(encoding.encode(x)))
df = df[df.n_tokens <= max_tokens].tail(top_n)
len(df)

if True:
    # write to file
    df["embedding"] = df.combined.apply(lambda x: get_embedding(x, model=embedding_model))
    df.to_csv(out_embeddings_path)
else:
    # to read embeddings after they've been written to file:
    df = pd.read_csv(out_embeddings_path)
    df["embedding"] = df.embedding.apply(literal_eval).apply(np.array)  # convert string to numpy array

matrix = np.vstack(df.embedding.values)
matrix.shape
#(100, 1536)

#for quick look at clusters and their content, 
# will use KMeans and assumption of number of clusters
from sklearn.cluster import KMeans


n_clusters = 4
kmeans = KMeans(n_clusters=n_clusters, init="k-means++", random_state=42)
kmeans.fit(matrix)
labels = kmeans.labels_
df["Cluster"] = labels
df.groupby("Cluster").Score.mean().sort_values()
#Cluster
#1    4.2600
#3    4.3125
#2    4.5200
#0    5.0000
#Name: Score, dtype: float64

from sklearn.manifold import TSNE
import matplotlib
import matplotlib.pyplot as plt

tsne = TSNE(n_components=2, perplexity=15, random_state=42, init="random", learning_rate=200)
vis_dims2 = tsne.fit_transform(matrix)
x = [x for x, y in vis_dims2]
y = [y for x, y in vis_dims2]
for category, color in enumerate(["purple", "green", "red", "blue"]):
    xs = np.array(x)[df.Cluster == category]
    ys = np.array(y)[df.Cluster == category]
    plt.scatter(xs, ys, color=color, alpha=0.3)
    avg_x = xs.mean()
    avg_y = ys.mean()
    plt.scatter(avg_x, avg_y, marker="x", color=color, s=100)

plt.title("Clusters identified visualized in language 2d using t-SNE")

# reading up on rate-limiting the openai.Completion.create requests

delay_in_seconds = 20

# Reading a review which belong to each group.
rev_per_cluster = 5
for i in range(n_clusters):
    print(f"Cluster {i} Theme:", end=" ")
    reviews = "\n".join(
        df[df.Cluster == i]
        .combined.str.replace("Title: ", "")
        .str.replace("\n\nContent: ", ":  ")
        .sample(rev_per_cluster, random_state=42)
        .values
    )
    response = client.completions.create(
        model="gpt-3.5-turbo-instruct",
        #model="text-davinci-003", see https://platform.openai.com/docs/guides/text-generation
        prompt=f'What do the following customer reviews have in common?\n\nCustomer reviews:\n"""\n{reviews}\n"""\n\nTheme:',
        temperature=0,
        max_tokens=64,
        top_p=1,
        frequency_penalty=0,
        presence_penalty=0)
    print(f'RESP = {response}\n', flush=True)
    #response = response.model_dump_json()
    print(response.choices[0].text.replace("\n", ""))
    sample_cluster_rows = df[df.Cluster == i].sample(rev_per_cluster, random_state=42)
    for j in range(rev_per_cluster):
        print(sample_cluster_rows.Score.values[j], end=", ")
        print(sample_cluster_rows.Summary.values[j], end=":   ")
        print(sample_cluster_rows.Text.str[:70].values[j])
    print(f"{100*'-'}\n")
    time.sleep(delay_in_seconds)
