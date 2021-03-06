# OMERO-scripts
Series of script to make use of OMERO

## Fiji scripts
Under `Fiji` folder, you will find scripts that use simple-omero-client to send/import images/tags/key-value-pairs... in OMERO.

### Installation
To make scripts work, download the latest version of [simple-omero-client-[version].jar](https://github.com/GReD-Clermont/simple-omero-client/releases) and copy it to the `Plugins` folder of Fiji. 

The `OMERO-java dependencies` are required to make this extension working. Download the .jar file from the [OMERO download page](https://www.openmicroscopy.org/omero/downloads/), under "OMERO Java". Copy it to the `Plugins` folder of Fiji. 

### Documentation
You can find all the documentation on how to use some of our scripts on our [Fiji wiki page](https://wiki-biop.epfl.ch/en/Image_Storage/OMERO/OmeroFiji), under `Analyze OMERO data using simple-omero-client`


## QuPath scripts
Under `QuPath` folder, you will find scripts to get an instance of OMERO and communicate with it.

### Dependencies and installation
To make scripts work, you will need three dependcies : 
- [simple-omero-client](https://github.com/GReD-Clermont/simple-omero-client)
- [qupath-extension-biop-omero](https://github.com/BIOP/qupath-extension-biop-omero)
- [OMERO.java](https://www.openmicroscopy.org/omero/downloads/)

For `simple-omero-client`, download the latest version of [simple-omero-client-[version].jar](https://github.com/GReD-Clermont/simple-omero-client/releases) and copy it in the folder `C:\QuPath_Common_Data_0.3\extensions`

For the last two dependencies, look at the readme of [qupath-extension-biop-omero](https://github.com/BIOP/qupath-extension-biop-omero) to know how to install.

### Documentation
You can find all the documentation on how to use this extension on our [QuPath wiki page](https://wiki-biop.epfl.ch/en/Image_Storage/OMERO/OmeroQuPath).
