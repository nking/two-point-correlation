import os
import math
import numpy as np

def calcDist(x0, x1):
    return math.sqrt(sum([(x0[i] - x1[i])**2 for i in range(len(x0))]))

def minMaxScaling(a):
    print(f"type={type(a)}\n")
    mina = min(a)
    rangea = max(a) - mina
    out = []
    for i in range(len(a)):
        out.append((a[i] - mina)/rangea)
    return out

def write_projected_to_file(proj, users_idx_to_id: dict, filename: str):
    csv_path2 = os.getcwd() + "/../../test/resources/" + filename
    with open(csv_path2, 'w', encoding="utf-8") as fp:
        #NOTE: i is uIdx in the projected matrix
        for i in range(len(proj)):
            line = f"{users_idx_to_id[i]},{proj[i][0]},{proj[i][1]}\n"
            _ = fp.write(line) # the _ captures the number of characters written.  if missing, that gets written to the CLI
        fp.close()

#userId2, userId2, diffs
def write_diffs_to_file(diffs, u_diffs, filename):
    csv_path2 = os.getcwd() + "/../../test/resources/" + filename
    with open(csv_path2, 'w', encoding="utf-8") as fp:
        #NOTE: i is uIdx in the projected matrix
        for i in range(len(diffs)):
            line = f"{u_diffs[i][0]},{u_diffs[i][1]},{diffs[i]}\n"
            _ = fp.write(line) # the _ captures the number of characters written.  if missing, that gets written to the CLI
        fp.close()

def write_crit_sep_to_file(crit_sep, filename) :
    csv_path2 = os.getcwd() + "/../../test/resources/" + filename
    with open(csv_path2, 'w', encoding="utf-8") as fp:
        line = f"{crit_sep}\n"
        _ = fp.write(line) # the _ captures the number of characters written.  if missing, that gets written to the CLI
        fp.close()

def crossValidationRiskEstimator(n_data, h, pEst):
    '''
    calculates the risk over histograms, comparable with other risk estimate
    comparisons over increasing bin widths for same pEst and n_data.
    decide best binwidth by minimum risk.

    Note that the algorithm expects that the data range of X (that went into creating pEst) was normalized
    to be between 0 and 1, inclusive.  Also, that h=1/m where m is pEst.length.

    Args:
      n_data: number of data points used to construct histogram pEst
      h: binwidth of histogram.  this was used in constructing histogram.
      pEst: y values of histogram, that is, the counts normalized

    reference:
        Wasserman's "All of Statistics" eqn (20.14).
        and
        https://en.m.wikipedia.org/wiki/Histogram
    '''
    m = len(pEst)
    sum = 0
    for j in range(0, m):
        sum += (pEst[j] * pEst[j])
    # from wikipedia, https://en.m.wikipedia.org/wiki/Histogram
    # reference to Stone 1984
    # AN ASYMPTOTICALLY OPTIMAL HISTOGRAM SELECTION RULE
    t1 = 2./((n_data-1.)*h)
    #t1 = 2./(n_data-1.);
    t2 = (n_data+1)/(n_data*n_data*h*(n_data-1.))
    jEst = t1 - t2*sum
    return jEst

prods = {}
users = {}
#csv_path = os.getcwd() + "/../../test/resources/amazon_fine_food_reviews_cleaned_sort_prod_pr_us_sc.csv"
csv_path = os.getcwd() + "/../../test/resources/amazon_fine_food_reviews_cleaned2_sort_prod_pr_us_sc.csv"
filename_out = "amazon_fine_food_reviews_cleaned2_projected.csv" # has 10% less entries, a little noise is removed.
#csv_path = os.getcwd() + "/../../test/resources/amazon_fine_food_reviews_cleaned_sort_prod_pr_us_sc_25.csv"
#csv_path = os.getcwd() + "/../../test/resources/amazon_fine_food_reviews_cleaned_sort_prod_pr_us_sc_100.csv"
with open(csv_path) as fp:
    for line in fp:
        pr,u,s = line.rsplit(",")
        if pr not in prods:
            prods[pr] = len(prods)
        if u not in users:
            users[u] = len(users)
    fp.close()

n_prods = len(prods)
n_users = len(users)

print(f"found {n_prods} products and {n_users} users\n")

# --- build sparse matrix ---

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

# rows are user idxs
# cols are product idxs
# values are scores
indexes = [[],[]]
data = []
#data_sk = []
# make reverse dictionaries to look up product and user id strings
prods_idx_to_id = {}
users_idx_to_id = {}
with open(csv_path) as fp:
    for line in fp:
        pr,u,s = line.rsplit(",")
        p_idx = prods[pr]
        u_idx = users[u]
        prods_idx_to_id[p_idx] = pr
        users_idx_to_id[u_idx] = u
        indexes[0].append(u_idx)
        indexes[1].append(p_idx)
        score = int(s.rstrip("\n"))
        data.append(score)
        #data_sk.append((u_idx, p_idx, score))

print(f"read the indices and data for sparse utility matrix\n")

#from scipy.sparse import coo_array
#sparse_utility_2 = coo_array((data, (indexes[0], indexes[1])), shape=(n_users, n_prods))
## can convert with .tocsr() etc

import torch
indexes = torch.tensor(indexes, )
data = torch.tensor(data, dtype=torch.float32)
sparse_utility = torch.sparse_coo_tensor(indexes, data, [n_users, n_prods])
print(f"have sparse utility matrix\n")
seed = 12345
torch.manual_seed(seed)
u,s,v = torch.svd_lowrank(sparse_utility, q=2, niter=2, M=None)

print(f"from SVD: u.shape={u.shape}, s.shape={s.shape}, v.shape={v.shape}\n")
print(f"s={s}\n")

# projected = sparse_utility * v
projected = torch.sparse.mm(sparse_utility, v)
assert(projected.shape[1] == 2)
print(f"projected.shape={projected.shape}\n")

p = projected.numpy()

# write to csv file.  userId[i], projected[i][0], projected[i][1]
write_projected_to_file(p, users_idx_to_id, filename_out)

import matplotlib.pyplot as plt
plt.scatter(projected[:,0].numpy(), projected[:,1].numpy(), s=1)
plt.show()

if True:
    exit()

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

'''
in density based clustering, we can find a critical separation between points and aggregate points within that
association distance to one another.

finding that critical separation using distances between points is n^2 which requires significant compute and RAM.

Instead of finding a critical separation using the entire dataset, we can extract a subset of data that we can
operate on.

For now, the choice of the subset is manual, but this could/should be done statistically.
(Yes, since choosing the subset is manual, it would be easy to determine the critical separation manually too,
but we will choose it with histograms to have code in place that works on a subset chosen statistically too.)

e.g.:

  (1) manually set subset region for p1 and p2 (from the s,v,d formed on my platform using the fixed random seed above)
  determine clustering point separation from:
  p1:    -0.5:0.0   -3.3:-1.25
  (2) apply to another regions also:
  p2:   -0.29:0.17  0.27:1.6

  (3) write output directory test/resources instead of bin/test-classes in order to keep the results for
  further analysis.

  write to test/resources:
      p as amazon_fine_food_reviews_projected.csv having userId[i], p[i][0], p[i][1]
      p1 as amazon_fine_food_reviews_projected_subset_1.csv having userId[i], p[i][0], p[i][1]
      diffs1 as amazon_fine_food_reviews_projected_subset_1_diffs.csv having userId1, userid2, diff
      p2 as amazon_fine_food_reviews_projected_subset_2.csv having userId, p[i][0], p[i][1]
      diffs2 as amazon_fine_food_reviews_projected_subset_2_diffs.csv having userId1, userid2, diff
      crit_sep as amazon_fine_food_reviews_projected_sep.txt having crit_sep
      amazon_fine_food_reviews_projected.png
      amazon_fine_food_reviews_projected_subset_1.png
      amazon_fine_food_reviews_projected_subset_2.png

  (4) in java, I have code to extract clusters given differences, class ClusterFinder3 so will use that
  instead of porting it to python.
     will use that to read in amazon_fine_food_reviews_projected_subset_1_diffs.csv and
     amazon_fine_food_reviews_projected_subset_2_diffs.csv and amazon_fine_food_reviews_projected_sep.csv
  (5) write to bin/test-classes/
      userId, cluster number  csv file and png file
'''

p1 = []
i_p1 = []
p2 = []
i_p2 = []
for i in range(len(p)):
    if p[i][0] >= -0.2377 and p[i][0] <= 0.05 and p[i][1] >= -1.724 and p[i][1] <= -1.11:
        p1.append([p[i][0], p[i][1]])
        i_p1.append(i)
    if p[i][0] >= -0.29 and p[i][0] <= 0.17 and p[i][1] >= 0.27 and p[i][1] <= 1.6:
        p2.append([p[i][0], p[i][1]])
        i_p2.append(i)

print(f"length of p1={len(p1)}\n") #1164
print(f"length of p2={len(p2)}\n") #2358

# write to csv file.  userId[i], projected[i][0], projected[i][1]
write_projected_to_file(p1, users_idx_to_id, "amazon_fine_food_reviews_projected_subset_1.csv")
write_projected_to_file(p2, users_idx_to_id, "amazon_fine_food_reviews_projected_subset_2.csv")

diffs = []
u_diffs = []
for i in range(len(p1)):
    for j in range(i+1, len(p1)):
        diffs.append(calcDist(p1[i], p1[j]))
        u_diffs.append((users_idx_to_id[i_p1[i]], users_idx_to_id[i_p1[j]]))

#userId2, userId2, diffs.   676866 lines
write_diffs_to_file(diffs, u_diffs, "amazon_fine_food_reviews_projected_subset_1_diffs.csv")

print(f"length of diffs={len(diffs)}\n")
#diffs_sc = minMaxScaling(diffs)

np.min(diffs), np.max(diffs), np.mean(diffs), np.median(diffs)
#np.min(diffs_sc), np.max(diffs_sc), np.mean(diffs_sc), np.median(diffs_sc)

'''
i = 10
min_r = 1E11
best_i = -1
while i < len(diffs_sc)/10:
    yh, xh = np.histogram(diffs_sc, bins=i, range=(0,1), density=False, weights=None)
    yh = yh/len(diffs_sc)
    if np.argmax(yh) > 0:
        # if we have enough resolution to find this
        best_i = i
        break
    r = crossValidationRiskEstimator(len(diffs_sc), xh[1]-xh[0], yh)
    if r < min_r:
        min_r = r
        best_i = i
    i = 10 * i
'''
from scipy.signal import argrelextrema
#looking for the peaks in a histogram of size 100, past index=0
yh, xh = np.histogram(diffs, bins=100, density=False, weights=None)
max_ind = argrelextrema(yh, np.greater)

crit_sep = xh[max_ind][0]
print(f"sep={crit_sep}\n")

write_crit_sep_to_file(crit_sep, "amazon_fine_food_reviews_projected_sep.txt")

diffs = []
u_diffs = []
for i in range(len(p2)):
    for j in range(i+1, len(p2)):
        diffs.append(calcDist(p2[i], p2[j]))
        u_diffs.append((users_idx_to_id[i_p2[i]], users_idx_to_id[i_p2[j]]))

#userId2, userId2, diffs
write_diffs_to_file(diffs, u_diffs, "amazon_fine_food_reviews_projected_subset_2_diffs.csv")
