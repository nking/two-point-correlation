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
   pip3 install hyperopt
   pip3 install xgboost
   pip3 install pandas
   pip3 install openai
   pip3 install tiktoken

NOTE: use of openai in the Amazon...TextSummaization.py code requires
you to get an openai api key and install it.
https://platform.openai.com/docs/quickstart?context=python

Misc other:

to run the code from command line and keep the python shell open
for interactive use:
   python3 -i <script>

to make a jupytper notebook:
   pip3 install jupyter
   jupyter notebook

I wasn't successful with converting jupyter notebook to pdf
outside of the browser normal print functions.
The notebook version 7 isn't currently compatible with various combinations of
of libraries including nbconvert, nbclassic, nor jupyter_contrib_nbextensions for
the conversion to pdf feature.
(the notebook_shim incorrectly references an obsolete notebook.base package)

