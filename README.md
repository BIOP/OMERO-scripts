# OMERO-scripts
Series of script to make use of OMERO

## Fiji scripts
Under `Fiji` folder, you will find scripts that use simple-omero-client to send/import images/tags/key-value-pairs... in OMERO.

### Installation
To make scripts work, download the latest version of [simple-omero-client-[version].jar](https://github.com/GReD-Clermont/simple-omero-client/releases) and copy it to the `Plugins` folder of Fiji. 

The `OMERO-java dependencies` are required to make this extension working. Download the .jar file from the [OMERO download page](https://www.openmicroscopy.org/omero/downloads/), under "OMERO Java". Copy it to the `Plugins` folder of Fiji. 

### Documentation
You can find all the documentation on how to use some of our scripts on our [Fiji wiki page](https://wiki-biop.epfl.ch/en/Image_Storage/OMERO/OmeroFiji), under `Analyze OMERO data using simple-omero-client`

### Available scripts
Scripts are grouped by categories. The folder "tag" groups all scripts that are dealing with tags.
All scripts, except advanced scripts, are basic and ready-to-use and show you the necessary commands to import, add, get and delete images, tables and annotations.

Advanced scripts perform deeper image analysis and parameters have to be changed to make the script working on your datasets.

## QuPath scripts
Under `QuPath` folder, you will find scripts to get an instance of OMERO and communicate with it.

### Dependencies and installation
To make scripts work, you will need three dependcies : 
- [simple-omero-client](https://github.com/GReD-Clermont/simple-omero-client)
- [qupath-extension-biop-omero](https://github.com/BIOP/qupath-extension-biop-omero)
- [OMERO.java](https://www.openmicroscopy.org/omero/downloads/)

For `simple-omero-client`, download the latest version of [simple-omero-client-[version].jar](https://github.com/GReD-Clermont/simple-omero-client/releases) and copy it in the folder `C:\QuPath_Common_Data_0.3\extensions`

For the last two dependencies, look at the readme of [qupath-extension-biop-omero](https://github.com/BIOP/qupath-extension-biop-omero) to know how to install.

### How to use scripts
- All the necessary methods to communicate with OMERO from QuPath are available under the static class `OmeroRawScripting`.
- Before running any script, you will need to have an image, coming from OMERO, open in QuPath.
- Download the script
- On QuPath, go under `Automate->Show script editor`
- Click on `File->Open` and select the script you want to run
- Click on `run->run`


