Barebones tool for creating CCDA Documents using the MDHT Project (https://github.com/mdht/mdht-models)

This project generates documents accoreding to the November 2020 version 2.1 C-CDA Implementation Guide

References for generating CCDA - http://www.hl7.org/ccdasearch/index.html
The latest PDF documentation can be found at: http://www.hl7.org/implement/standards/product_brief.cfm?product_id=492

/repo contains all of the MDHT dependencies required for producing and manipulating CCDA documents. These jars are not available on Maven so we packaged them with this project.

script.py (http://github.com/nikita-volkov/install-to-local-repo) can be run to generate a local dependency repository for the MDHT dependencies whenever a new major version is released.

