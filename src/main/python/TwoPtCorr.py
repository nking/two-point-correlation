from numpy import sqrt, array, mean, std, log2, percentile, exp, linspace
import fast_histogram
from math import ceil
from collections import Counter, defaultdict
from scipy.spatial import cKDTree
#import scipy.stats
#import matplotlib.pyplot as plt
from scipy.signal import find_peaks, peak_prominences

'''
NOTE: this critical point separation method is not precisely the same as the java code.
Neither this code nor the java code premise scale well with increasing dimension
because euclidean distance differences diminish with increasing dimension.
'''

class UnionFind:
    def __init__(self, _n):
        self.p = [i for i in range(0, _n)]
        self.r = [0 for i in range(0, _n)]
        self.n_components = _n

    def find(self, u):
        if self.p[u] != u :
            self.p[u] = self.find(self.p[u])
        return self.p[u]

    def union(self, u, v):
        p_u = self.find(u)
        p_v = self.find(v)
        if p_u == p_v:
            return False
        if self.r[u] > self.r[v]:
            self.p[v] = p_u
        elif self.r[u] < self.r[v]:
            self.p[u] = p_v
        else:
            self.p[v] = p_u
            self.r[u] += 1
        #this will be wrong once v or u has been unioned more than once
        self.n_components -= 1
        return True

class TwoPtCorr:
    def __init__(self):
        self.thresh = 2.5
        self.threshsq = self.thresh**2

    def dist2(self, X, i, j):
        return sqrt((X[i][0]-X[j][0])**2 + (X[i][1]-X[j][1])**2)

    def num_bins(self, diffs):
        n = len(diffs)
        #Freedman-Diaconis rule
        '''
        double[] medianAndIQR = MiscMath0.calcMedianAndIQR(data);
        double d = Math.pow(data.length, 1./3.);
        return (int) (2. * medianAndIQR[1]/d);
        '''
        # from https://medium.com/@maxmarkovvision/optimal-number-of-bins-for-histograms-3d7c48086fde
        p25, p75 = percentile(diffs, [25, 75])
        width = 2. * (p75 - p25) / n ** (1. / 3)
        mx = diffs.max()
        mn = diffs.min()
        nbins = ceil((mx - mn) / width)
        return max(1, nbins)

    def label(self, p):
        cluster_min = 4
        c = Counter(p)
        bkey = 0
        #count number w/ >= cluster_min
        for key in c:
            if c.get(key) >= cluster_min:
                bkey += 1
        m = defaultdict()
        g = 0
        lab = []
        for i in range(0, len(p)) :
            if c.get(p[i]) < cluster_min:
                if p[i] not in m:
                    m[p[i]] = bkey
            elif p[i] not in m:
                m[p[i]] = g
                g += 1
            lab.append(m[p[i]])
        #c = Counter(lab)
        #print(f'{c.get(bkey)} are not in clusters.  there are {len(c)-1} clusters.\n')
        return array(lab)

    def fit(self, X):
        n_samples, _ = X.shape
        diffs = []
        for i in range(0, n_samples) :
            diff = sqrt(((X[i] - X[i+1:len(X)])**2).sum(axis=1))
            diffs.extend(diff)
        diffs = array(diffs)
        # the sep peak is in first half of sorted diffs, so filter to improve resolution:
        diffs = diffs[diffs <= percentile(diffs, [50])]
        #caveat: very dependent upon histogram bin width.
        # better results possibly w/ kernel smoothing or those in java code that use area of peak (w/ GEV in mind)
        #'''
        n_bins = self.num_bins(diffs)
        r = (diffs.min(), diffs.max())
        h = fast_histogram.histogram1d(diffs, n_bins, r)
        h_idx = h.argmax()
        hgrid = linspace(r[0], r[1], n_bins)
        bin_width = (r[1] - r[0]) / n_bins
        self.sep = r[0] + h_idx * bin_width
        peaks, props = find_peaks(h)
        prominences, left_base, right_base = peak_prominences(h, peaks)
        # last point before right_base >= factor 2
        r_idx = 0
        for i in range(1, len(right_base)):
            if (right_base[i]/right_base[i-1]) >= 2:
                r_idx = i
                break
        if r_idx == 0 :
            self.critSep = hgrid[peaks[0] - 1]
        else:
            self.critSep = hgrid[peaks[r_idx] - 1]
        '''print(f'diffs.min, max={diffs.min(), diffs.max()}, critSep={self.critSep}, sep={self.critSep/self.thresh}\n')
        plt.plot(hgrid, h)
        plt.tight_layout()
        plt.xlabel('Value')
        plt.ylabel('Density')
        plt.title('histogram')
        plt.show()
        '''

        #NOTE: for the DBScan 2 circles,
        # self.sep = 1.2065
        #self.critSep = self.sep / self.thresh
        uf = UnionFind(n_samples)
        '''
        ii = 0
        for i in range(0, n_samples) :
            for j in range(i+1, n_samples):
                if abs(diffs[ii]) <= self.critSep :
                    uf.union(i, j)
                ii += 1
        print(f'n_groups={uf.n_components}\n')
        '''
        diffs = []
        #'''
        tree = cKDTree(X)
        for i in range(0, n_samples) :
            dist, indices = tree.query([X[i]], k=n_samples, distance_upper_bound=self.critSep, p=2)
            for j in indices.flatten():
                if j == n_samples:
                    break
                if i != j:
                    uf.union(i, j)
        #'''
        self.labels_ = self.label(uf.p)

