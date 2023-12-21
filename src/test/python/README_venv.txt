to create a venv:
   python3 -m venv venv
   (the later can be named anything, but venv is standard)

to activate venv:
   source venv/bin/activate
   (or equiv for other than unix)

to confirm that venv is activated:
   which python

to deactivate:
   deactivate

to import packages into this venv after it has been activated:
   # might need to install jaxlib if not handled automatically in jax install
   pip3 install numpy
   pip3 install torch
   pip3 install matplotlib
   pip3 install scikit-learn
   pip3 install graphviz
   pip3 install plotly
   pip3 install six
   pip3 install ipywidgets
   pip3 install jupyter_contrib_nbextensions
   pip3 install catboost
   pip3 install shap

Misc other:

to run the code from command line and keep the python shell open
for interactive use:
   python3 -i <script>

to make a jupytper notebook:
   pip3 install jupyter
   jupyter notebook
