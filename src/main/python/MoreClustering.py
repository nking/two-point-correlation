from sklearn import cluster, datasets, mixture
from sklearn.preprocessing import StandardScaler
import matplotlib.pyplot as plt
import numpy as np
import time
import warnings
from itertools import cycle, islice

'''
consider adding VAE w/ normalizing flows (though computationally expensive
    unless using fast jacobians like triangular jacobians to make
    inverse of function faster):
    https://youtu.be/qgTvgBCOyn8?si=W-1uh5oLMM-AaDqx
    -- see NICE additive coupling layers.
       partitions latent variables z into 2 disjoint subsets, the 2nd of which
       becomes shifts of the first partition. the forward mapping
       results in det|Jacobian|=1
       so a neural network can use several NICE layers to be very expressive.
       - is volume preserving.
    -- see use of Real NVP which scales in addition to the shift of NICE:
       https://github.com/VincentStimper/resampled-base-flows
       https://pypi.org/project/normflows/
       - is not volume preserving.
    -- masked autoregresive flow:
       - continuous autoregressive models can be considered flow models using gaussian
       - can use MADE to compute in parallel the parameters
         - likelihood eval in parallel too
         - lower tridiagonal jacobian to speed up inversion
         - but sampling is sequential and slow
           -- Inverse Autoregressive Flow attempts to address the sampling bottleneck
              but the trade-off is that evaluating likelihoods becomes sequential and
              slow.  hard to use during training, but easy to use on data that is generated
              via cached z1, z2, ....
         - like autoregressive models, the likelihoods can be computed exactly
    => MAF : fast likelihhod eval, slow sequential sampling
             good for fast raining
       IAF : fast sampling, slow sequential likelihood eval. 
             good for fast real-time generation
       see:
           https://goodboychan.github.io/python/coursera/tensorflow_probability/icl/2021/09/08/01-AutoRegressive-flows-and-RealNVP.html
           though it is not unsupervised, like the 2ptcorr, dbscan, and mixture model below
    ==> parallel wavenet used a distilled MAF then IAF using likehood eval during training
        and can be run in parallel.  wavenet is not masked.
    -- gausianization flows

'''

# adapted from https://scikit-learn.org/stable/auto_examples/cluster/plot_cluster_comparison.html

from TwoPtCorr import TwoPtCorr

n_samples = 500
seed = 30
noisy_circles = datasets.make_circles(
    n_samples=n_samples, factor=0.5, noise=0.05, random_state=seed
)

blobs = datasets.make_blobs(n_samples=n_samples, random_state=seed)

# ============
# Set up cluster parameters
# ============
plt.figure(figsize=(3 * 2 + 3, 5))
plt.subplots_adjust(
    left=0.02, right=0.98, bottom=0.001, top=0.95, wspace=0.05, hspace=0.01
)

plot_num = 1

default_base = {
    "quantile": 0.3,
    "eps": 0.3,
    "damping": 0.9,
    "preference": -200,
    "n_neighbors": 3,
    "n_clusters": 3,
    "min_samples": 7,
    "xi": 0.05,
    "min_cluster_size": 0.1,
    "allow_single_cluster": True,
    "hdbscan_min_cluster_size": 15,
    "hdbscan_min_samples": 3,
    "random_state": 42,
}

datasets = [
    (
        noisy_circles,
        {
            "damping": 0.77,
            "preference": -240,
            "quantile": 0.2,
            "n_clusters": 2,
            "min_samples": 7,
            "xi": 0.08,
        },
    ),
    (blobs, {"min_samples": 7, "xi": 0.1, "min_cluster_size": 0.2}),
 ]


for i_dataset, (dataset, algo_params) in enumerate(datasets):
    # update parameters with dataset-specific values
    params = default_base.copy()
    params.update(algo_params)

    X, y = dataset

    # normalize dataset for easier parameter selection
    X = StandardScaler().fit_transform(X)

    dbscan = cluster.DBSCAN(eps=params["eps"])

    #gmm = mixture.GaussianMixture(
    #    n_components=params["n_clusters"],
    #    covariance_type="full",
    #    random_state=params["random_state"],
    #)
    gmm = mixture.BayesianGaussianMixture(
        n_components=params["n_clusters"],
        covariance_type="full",
        random_state=params["random_state"],
        weight_concentration_prior_type='dirichlet_process'
    )

    #NOTE: this is not set-up as a solver because it's not tuned like the java code
    twoptcorr = TwoPtCorr()

    clustering_algorithms = (
        ("twoptcorr", twoptcorr),
        ("DBSCAN", dbscan),
        ("Gaussian\nMixture", gmm),
    )

    for name, algorithm in clustering_algorithms:
        t0 = time.time()

        # catch warnings related to kneighbors_graph
        with warnings.catch_warnings():
            warnings.filterwarnings(
                "ignore",
                message="the number of connected components of the "
                + "connectivity matrix is [0-9]{1,2}"
                + " > 1. Completing it to avoid stopping the tree early.",
                category=UserWarning,
            )
            warnings.filterwarnings(
                "ignore",
                message="Graph is not fully connected, spectral embedding"
                + " may not work as expected.",
                category=UserWarning,
            )
            algorithm.fit(X)

        t1 = time.time()
        if hasattr(algorithm, "labels_"):
            y_pred = algorithm.labels_.astype(int)
        else:
            y_pred = algorithm.predict(X)

        plt.subplot(len(datasets), len(clustering_algorithms), plot_num)
        if i_dataset == 0:
            plt.title(name, size=18)

        colors = np.array(
            list(
                islice(
                    cycle(
                        [
                            ## from https://godsnotwheregodsnot.blogspot.com/2012/09/color-distribution-methodology.html
                            "#FFFF00", "#1CE6FF", "#FF34FF", "#FF4A46", "#008941", "#006FA6", "#A30059",
"#FFDBE5", "#7A4900", "#0000A6", "#63FFAC", "#B79762", "#004D43", "#8FB0FF", "#997D87",
"#5A0007", "#809693", "#FEFFE6", "#1B4400", "#4FC601", "#3B5DFF", "#4A3B53", "#FF2F80",
"#61615A", "#BA0900", "#6B7900", "#00C2A0", "#FFAA92", "#FF90C9", "#B903AA", "#D16100",
"#DDEFFF", "#000035", "#7B4F4B", "#A1C299", "#300018", "#0AA6D8", "#013349", "#00846F",
"#372101", "#FFB500", "#C2FFED", "#A079BF", "#CC0744", "#C0B9B2", "#C2FF99", "#001E09",
"#00489C", "#6F0062", "#0CBD66", "#EEC3FF", "#456D75", "#B77B68", "#7A87A1", "#788D66",
"#885578", "#FAD09F", "#FF8A9A", "#D157A0", "#BEC459", "#456648", "#0086ED", "#886F4C",
"#34362D", "#B4A8BD", "#00A6AA", "#452C2C", "#636375", "#A3C8C9", "#FF913F", "#938A81",
"#575329", "#00FECF", "#B05B6F", "#8CD0FF", "#3B9700", "#04F757", "#C8A1A1", "#1E6E00",
"#7900D7", "#A77500", "#6367A9", "#A05837", "#6B002C", "#772600", "#D790FF", "#9B9700",
"#549E79", "#FFF69F", "#201625", "#72418F", "#BC23FF", "#99ADC0", "#3A2465", "#922329",
"#5B4534", "#FDE8DC", "#404E55", "#0089A3", "#CB7E98", "#A4E804", "#324E72", "#6A3A4C", "#000000"
                            '''
                            "#377eb8",
                            "#ff7f00",
                            "#4daf4a",
                            "#f781bf",
                            "#a65628",
                            "#984ea3",
                            "#999999",
                            "#e41a1c",
                            "#dede00",
                            '''
                        ]
                    ),
                    #int(max(y_pred) + 1),
                    int(max(y_pred)),
                )
            )
        )
        # add black color for outliers (if any)
        colors = np.append(colors, ["#000000"])
        plt.scatter(X[:, 0], X[:, 1], s=10, color=colors[y_pred])

        plt.xlim(-2.5, 2.5)
        plt.ylim(-2.5, 2.5)
        plt.xticks(())
        plt.yticks(())
        plt.text(
            0.99,
            0.01,
            ("%.2fs" % (t1 - t0)).lstrip("0"),
            transform=plt.gca().transAxes,
            size=15,
            horizontalalignment="right",
        )
        plot_num += 1

plt.show()
