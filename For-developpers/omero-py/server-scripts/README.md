# Add owner as key value

# Dataset to plate

# Duplicate images
## Description

 This script duplicates, n times, all images contained in the selected container(s), as well as their children if they have. The containers can be
 - Image 
 - Dataset
 - Project
 - Well
 - Plate
 - Screen
 
 For `Image`, a dataset is automatically created called `duplicated_images_n` so that images are not landed into the orphaned folder.
 
 ## How to install it
 ### Modify the script
 You have to modify the variables `OMERO_SERVER` `PORT`, and `OMERO_WEBSERVER` with your own server addresses & port.
 
 ### Server side
- You need to install https://github.com/ome/omero-cli-duplicate on the server to make the script work.
- Upload the script on the server to make it accessible from omero-web.

## How to use it
- Select the container(s) you would like to duplicate on omero-web
- Open the script ; the 2 first fields should be filled automatically
- Select the number of duplication you would like to do (min: 1)
- Run the script
 
## Expected output
- Images and containers duplication
- On each image, a key-value pair with the duplication time & author (under the `omero-duplicate` namespace)
- On each container, a key-value pair with the URL of the duplicated source container(s) (under the `omero-duplicate` namespace)

# Export Cellprofiler IDs

# Intensity Projection

## Description
Script performing an intensity projection (max or min projection) along Z axis for all selected images. 

## How to install it
- Upload the script on the server to make it accessible from omero-web.

## How to use it
- Select on omero-web the image(s) you want to make z-projection on.
- Open the script ; the 2 first fields should be filled automatically.
- Check the box if you would like to perform the projection on the full stack. In that case, you don't need to enter any value under `Starting Z` nor `Ending Z` 
- If you prefer to select only part of the stack, then check the box and enter from which slice to which slice you want to perform the projection.
- Select the projection type (`max` or `min`)
- Check the box to also transfer annotation to the projection image. ROIs and image description are not supported.
- Run the script
 
## Expected output
- A new image(s) on OMERO corresponding to the z-projection of the selected stack(s)
- Linked to each projection image, a `min_projection` or `max_projection` tag
- Linked to each projection image, a key-value pair with the source image, projection type and projected slices, under the `z-projection` namespace