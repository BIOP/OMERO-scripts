
# Script description
- [upload-comet-images](#upload-comet-images)


## Upload comet images
### Description

This script imports image(s) on OMERO, with one or more attachments.

### How to install it
Create a python environment with the following dependencies
- pyqt6
- ezomero

```
conda create -n omero-env python=3.9
conda activate omero.env
conda install -c conda-forge zeroc-ice==3.6.5
pip install pyqt ezomero
```

### How to use it
- Open a conda/miniforge... terminal
- Write down the following commands

```
conda activate omero-env
python "path/to/upload-comet-images.py" 
```


- In the script GUI:
  - `Username`: OMERO username
  - `Password` : OMERO password
  - Click on `Connect`
  - `Group` : Select the group where to import your images
  - `Project` : Select an existing project from the drop-down menu or create a new one. Images will be imported under that project
  - `Dataset` : Select an existing dataset from the drop-down menu or create a new one. Images will be imported under that dataset
  - `Image path` : Give the path to the image to import. Only one image can be selected in this field
  - `Attachment` : Select one or more attachments to link to the above image. Leave blank if you don't want to attach anything.
  - Click on `Next` to prepare another job (i.e. importing another image)
  - Click on `Ok` to start the import of all the jobs


### Expected output
- The selected image should be imported on OMERO, in the right dataset/project
- The selected attachment(s) should be uploaded and linked to the image
