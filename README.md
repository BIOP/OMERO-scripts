# Teaching
This folder groups all the scripts used during the OMERO training and the ones referenced in the examples described in [the OMERO-Fiji documentation](https://wiki-biop.epfl.ch/en/data-management/omero/fiji)

# Users
This folder groups scripts for general purposes that can be directly used by any users. These scripts are as generic as possible. They are provided with 
- a simple GUI at the beginning 
- Popup messages to inform in case of error
- ImageJ Logs to follow what the script is doing
- A CSV report which summarizes what has been successfully done and what has been failed.

They can all be customized according to your needs. Please contact us in such case. **This service will only be given for EPFL students.**

# Developpers
This folder groups template scripts to interact with OMERO. These scripts implement simple commands on OMERO features. They are not intended to be used directly ; 
the purpose of those scripts is to help people developping their own scripts with the ``simple-omero-client`` API. No popup messages neither error catching are implemented in the template scripts.
