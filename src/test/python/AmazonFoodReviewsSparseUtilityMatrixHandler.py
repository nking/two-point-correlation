import os
import math

def calcDist(x0, x1) {
    return math.sqrt(sum([(x0[i] - x1[i])**2 for i in range(len(x0))]))

def minMaxScaling(double[] a) {
    min = min(a)
    range = max(a) - min
    for i in range(len(a)):
        a[i] = (a[i] - min)/range

prods = {}
users = {}
csv_path = os.getcwd() + "/../../test/resources/amazon_fine_food_reviews_cleaned_sort_prod_pr_us_sc.csv"
with open(csv_path) as fp:
    for line in fp:
        pr,u,s = line.rsplit(",")
        if pr not in prods:
            prods[pr] = len(prods)
        if u not in users:
            users[u] = len(users)

n_prods = len(prods)
n_users = len(users)

print(f"found {n_prods} products and {n_users} users")

# --- build sparse matrix ---

'''
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

with open(csv_path) as fp:
    for line in fp:
        pr,u,s = line.rsplit(",")
        p_idx = prods[pr]
        u_idx = users[u]
        indexes[0].append(u_idx)
        indexes[1].append(p_idx)
        data.append(int(s.rstrip("\n")))

print(f"read the indices and data for sparse utility matrix")

import torch

indexes = torch.tensor(indexes)
data = torch.tensor(data, dtype=torch.float32)
sparse_utility = torch.sparse_coo_tensor(indexes, data, [n_users, n_prods])

print(f"have sparse utility matrix")

u,s,v = torch.svd_lowrank(sparse_utility, q=2, niter=2, M=None)

print(f"from SVD: u.shape={u.shape}, s.shape={s.shape}, v.shape={v.shape}\n")
print(f"s={s}\n")

# projected = sparse_utility * v
projected = torch.sparse.mm(sparse_utility, v)
assert(projected.shape[1] == 2)

# write to csv file
csv_path = os.getcwd() + "/../../test/resources/amazon_fine_food_reviews_cleaned_projected.csv"
with open(csv_path, 'w', encoding="utf-8") as fp:
    for i in range(projected.shape[0]):
        line = f"{projected[i][0]},{projected[i][1]}\n"
        fp.write(line)

# faster to use KMeans and xmeans though not determinate, but for now, using density based clustering:
diffs = []
for i in range(projected.shape[0]):
    for j in range(i+1, projected.shape[0]):
        diffs = calcDist(projected[i], projected[j])

minMaxScaling(diffs)

# sturges for nBins approx
nbins = 1 + int(math.ceil(math.log(len(diffs)/math.log(2)))

nBins = [i for i in range(5, nbins)]

# paused here