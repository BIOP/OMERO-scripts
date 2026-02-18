# Uploading on server
The scripts can be placed in the `OMERODIR/lib/scripts/omero` directory of your omero installation, 
preferentially in a separate subdirectory, e.g. `Utils_scripts/.`

Follow these [instructions](https://omero.readthedocs.io/en/stable/developers/scripts/index.html#downloading-and-installing-scripts) 
to install/update the scripts.

# Script description
- [Add owner as key value](#add-owner-as-key-value)
- [Dataset to plate](#dataset-to-plate)
- [Duplicate images](#duplicate-images)
- [Export Cellprofiler IDs](#export-cellprofiler-ids)
- [Intensity Projection](#intensity-projection)
- [Import from csv](#import-from-csv)
- [Merge plate run](#merge-plate-run)
- [Share images across groups](#share-images-across-groups)


## Add owner as key value
### Description

This script adds the owner name as a key-value pair to all images contained in the selected container(s).

### How to install it
#### Upload
- Have a look to [Upload](#uploading-on-server) section.

### How to use it
- Select the container(s) you would like to duplicate on omero-web

> Note: If you want to add ownership to all images belonging to a particular user, 
> you don't need to select any container. Just open the script and enter
> - `User` in the **Data Type** field.
> - the omero username in the **IDs** field.

- Open the script:
  - `Data Type`: should be filled automatically 
  - `IDs` : should be filled automatically.
- Run the script

### Expected output
- On each image, a key-value pair with the image owner name & date, under the namespace `data.ownership`

 
## Dataset to plate
### Description

Take a Dataset of images and put them in a new Plate, arranging them into rows or columns as desired.
Optionally add the Plate to a new or existing Screen.<br>
Optionally read well & run position from the image name.

> Note: This script is based on the official script 
> https://github.com/ome/omero-scripts/blob/develop/omero/util_scripts/Dataset_To_Plate.py, written by the OME-Team

### How to install it
#### Modify the script

At the beginning of the script
- Create a new image pattern name (like `EVOS_TEMPLATE`) that will be displayed in the drop-down menu
- Create the corresponding regex to match the image name
- Add both pattern name and regex into the map `position_template_map`

#### Upload
- Have a look to [Upload](#uploading-on-server) section.

### How to use it
- Select on omero-web the dataset(s) you want to convert into plates
- Open the script
  - `Data Type`: should be filled automatically 
  - `IDs` : should be filled automatically.
  - `Filter names`: enter a string that is common to all images you would like to process
  - ``Read Well From Image Name``: Optional field to read well position and run info on the image name directly.
  If you choose this option, you don't have to enter any of the value below `First Axis` group.
  - `Pattern`: Choose the pattern name following your image convention names (only matters if you selected this option)

For the rest of the fields, have a look to the official documentation.

### Expected output
- New screen, with a new plate containing images in the corresponding wells.

## Duplicate images
### Description

 This script duplicates, n times, all images contained in the selected container(s), as well as their children if they have. The containers can be
 - Image 
 - Dataset
 - Project
 - Well
 - Plate
 - Screen
 
 For `Image`, a dataset is automatically created called `duplicated_images_n` so that images are not landed into the orphaned folder.
 
 ### How to install it
 #### Modify the script
-  You have to modify the variables `OMERO_SERVER` `PORT`, and `OMERO_WEBSERVER` with your own server addresses & port.
 
 #### Server side
- You need to install https://github.com/ome/omero-cli-duplicate on the server to make the script work.

#### Upload
- Have a look to [Upload](#uploading-on-server) section.

### How to use it
- Select the container(s) you would like to duplicate on omero-web
- Open the script:
  - `Data Type`: should be filled automatically 
  - `IDs` : should be filled automatically.
  - `Number of duplicate to create` : Select the number of duplication you would like to do (min: 1)
- Run the script
 
### Expected output
- Duplicated images and containers
- On each image, a key-value pair with the duplication time & author (under the `omero-duplicate` namespace)
- On each container, a key-value pair with the URL of the duplicated source container(s) (under the `omero-duplicate` namespace)

## Export Cellprofiler IDs
### Description
This script exports in a txt file, with OMERO IDs, formatting in a way that CellProfiler
is able to read the images from OMERO and apply a pipeline on them.

### How to install it
#### Upload
- Have a look to [Upload](#uploading-on-server) section.

### How to use it
- Select the container(s) you would like to process on omero-web
- Open the script:
  - `Data Type`: should be filled automatically 
  - `IDs` : should be filled automatically.
  - `Target Data Type` : Select the type of data to process ; should be `Image` in that case.
- Run the script

### Expected output
- .txt file attached to the selected container, with OMERO IDs readable by cellProfiler. 


## Import from csv
This script is a duplicate of the [official script](https://github.com/ome/omero-scripts/blob/develop/omero/annotation_scripts/Import_from_csv.py), where a new option has been added.

The documentation of the script is available on the [official website](https://guide-kvpairs-scripts.readthedocs.io/en/latest/walkthrough.html#importing-key-value-pairs)
 as well the [parameter description](https://guide-kvpairs-scripts.readthedocs.io/en/latest/parameter_description.html).

### New option description
A checkbox `Group keys by unicity of values` is added to the GUI, in the `Other parameters` group.
If you check this box, key-values will be analysed and grouped according to unicity of values
- For each key, if the value for all images are identical, those keys will be grouped under the same KVP group
- For each key, if the value for images are different, then each key will be added in a separate KVP group.

## Intensity Projection

### Description
Script performing an intensity projection (max or min projection) along Z axis for all selected stacks. 

### How to install it
#### Upload
- Have a look to [Upload](#uploading-on-server) section.

### How to use it
- Select on omero-web the stack(s) you want to make z-projection on.
- Open the script : 
  - `Data Type`: should be filled automatically 
  - `IDs` : should be filled automatically.
  - `Full stack projection`: Check the box if you would like to perform the projection on the full stack. 
  In that case, you don't need to enter any value under `Starting Z` nor `Ending Z`.
  - `Starting Z`: First slice of the projection. Only used if you don't do a full stack projection.
  - `Ending Z`:  Last slice of the projection. Only used if you don't do a full stack projection.
  - `Projection type`:  select the projection type `max` or `min`
  - `Transfer annotations` : Check the box to also transfer annotation to the projection image. ROIs and image description are not supported.
- Run the script
 
### Expected output
- A new image(s) on OMERO corresponding to the z-projection of the selected stack(s)
- Linked to each projection image, a `min_projection` or `max_projection` tag
- Linked to each projection image, a key-value pair with the source image, projection type and projected slices, under the `z-projection` namespace

## Merge plate run
### Description
This script is not hosted on our GitHub, as it used as is on our server.
You can find the script & documentation on the [cai-hhu GitHub](https://github.com/cai-hhu/omero-HCS-utility/tree/main/scripts/omero/hcs_scripts).
 ### How to install it
#### Upload
- Have a look to [Upload](#uploading-on-server) section.

## Share images across groups
### Description

This script duplicates selected images in the specified dataset and transfer 
the dataset from the current group to the specified target group.


 ### How to install it
 #### Modify the script
 - You have to modify the variables `OMERO_SERVER` `PORT`, and `OMERO_WEBSERVER` with your own server addresses & port.
 
 #### Server side
- You need to install https://github.com/ome/omero-cli-duplicate on the server to make the script work.
#### Upload
- Have a look to [Upload](#uploading-on-server) section.

### How to use it
- Select on omero-web the images(s) you would like to share.
- Open the script:
  - `Data Type`: should be filled automatically 
  - `IDs` : should be filled automatically.
  - `Target group` : Exact name of the group to transfer the images to (NOT case-sensitive) OR group ID
  - `Dataset`: Name of the dataset which will contain the duplicated images. Leave blank to put in orphaned folder.
- Run the script
 
### Expected output
- Duplicated images in the target group
- On each image, a key-value pair with the duplication time & author and 
the base & target group (under the `omero-duplicate` namespace)
- On the dataset (if there is one), a key-value pair with the URL of the duplicated source images(s) (under the `omero-duplicate` namespace)
