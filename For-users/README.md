# Installation
The scripts are based on [simple-omero-client](https://github.com/GReD-Clermont/simple-omero-client) API and are running on Fiji. Groovy language is used to interact with OMERO.

- Download the latest version of [simple-omero-client-x.x.x.jar](https://github.com/GReD-Clermont/simple-omero-client/releases) 
- Download the `OMERO-java dependencies` .jar file from the [OMERO download page](https://www.openmicroscopy.org/omero/downloads/), under ``ImageJ / Fiji``.

Copy both jars in the `Plugins` folder of Fiji. 

The name of each script is intended to give the general purpose of the script.
A full description of what the script does is written below.


# Script description
- [Add Key Values to Operetta images](#add-key-values-to-operetta-images-on-omero)
- [Auto-Tag Images on OMERO](#auto-tag-images-on-omero)
- [Batch Import and Tag Images to OMERO](#batch-import-and-tag-images-to-omero)
- [Batch Upload FV4000 Tiling Acquisitions to OMERO](#batch-upload-fv4000-tiling-acquisitions-to-omero)
- [Delete Orphaned Annotations on OMERO](#delete-orphaned-annotations-on-omero)
- [Delete Unlinked File Attachments on OMERO](#delete-unlinked-file-attachments-on-omero)
- [Download Images from OMERO Containers](#download-images-from-omero-containers)
- [Download Images from OMERO Figures](#download-images-from-omero-figures)
- [Extract Channel Names from OMERO images](#extract-channel-names-from-omero-images)
- [List Images and hierarchy from OMERO Container](#list-images-and-hierarchy-from-omero-containers)
- [Replace and Clean Tags on OMERO](#replace-and-clean-tags-on-omero)
- [Transfer Annotations Between OMERO Images](#transfer-annotations-between-omero-images)
- [Z-Projection of OMERO images](#z-projection-of-omero-images)



# Add Key-Values to Operetta Images on OMERO

## Description
This Fiji script reads a local CSV file containing plate layout information and adds the corresponding **key-value pairs** to each well and its images within a specified OMERO plate or screen. The CSV file must follow a specific format where the first two columns define the well position (row and column) and the remaining columns define the key-value pairs to attach. A CSV report and a Fiji log file are saved upon completion.

> The expected CSV format is: `Row, Column, Key1, Key2, Key3, ...` where each row corresponds to one well, with a header.

---

## Inputs

### SciJava Parameters
| Parameter           | Description                                                                              |
|---------------------|------------------------------------------------------------------------------------------|
| `Host`              | OMERO server address                                                                     |
| `Username`          | OMERO username                                                                           |
| `Password`          | OMERO password (not persisted)                                                           |
| `Object to process` | Type of OMERO container to process: `plate` or `screen`                                 |
| `Object ID`         | OMERO ID of the selected plate or screen                                                 |
| `CSV file`          | Local CSV file containing the plate layout with well positions and key-value information |

---

## Outputs

| Output | Description |
|--------|-------------|
| **Key-values on OMERO** | Key-value pairs attached to each well and its images in the specified plate(s)/screen |
| **CSV report** | Summary per image: image name/ID, well name, plate name, screen name, key-values added, and status — saved in `~/Downloads/` |
| **Log file** | Fiji log window saved as a `.txt` file alongside the CSV report |



# Auto-Tag Images on OMERO

## Description
This Fiji script automatically adds tags to images already stored in one or more OMERO datasets, based on the parsing of image names and/or serie names. Tokens extracted from the names are created as tags and linked to the corresponding images on OMERO. Purely numeric tokens are ignored. A CSV report and a Fiji log file are saved upon completion.

> **Note:** Unlike the OMERO.web AutoTag plugin, this script does **not** use the original import path to generate tags.

---

## Inputs

### SciJava Parameters
| Parameter        | Description                                                                                  |
|------------------|----------------------------------------------------------------------------------------------|
| `Host`           | OMERO server address                                                                         |
| `Username`       | OMERO username                                                                               |
| `Password`       | OMERO password (not persisted)                                                               |
| `Datasets ID`    | ID(s) of the dataset(s) to process. Separate multiple IDs with a semicolon `;`              |
| `Image name tags`| Parse the image filename (split on `_`, space, `/`, `\`) to generate tags                   |
| `Serie name tags`| Parse the serie name (split on `_`, space, `/`, `\`, `,`) to generate tags                  |

---

## Outputs

| Output | Description |
|--------|-------------|
| **Tags on OMERO** | Tags parsed from image name and/or serie name, linked to each image in the specified dataset(s) |
| **CSV report** | Summary per image: dataset name/ID, image name/ID, tags added, and processing status — saved in `~/Downloads/` |
| **Log file** | Fiji log window saved as a `.txt` file alongside the CSV report |


# Batch Import and Tag Images to OMERO

## Description
This Fiji script batch uploads images from a local folder (including all subfolders) into a single OMERO dataset. After upload, it automatically parses image names, serie names, and/or folder hierarchy to create and attach tags to the images on OMERO. A CSV report and a Fiji log file are saved upon completion.

> **Notes:**
> - `.xml`, `.xdce`, `.ome` and HCS/screen formats are **not supported**
> - For fileset OME-TIFF images, group each fileset in its own subfolder before running the script

---

## Inputs

### SciJava Parameters
| Parameter  | Description                          |
|------------|--------------------------------------|
| `Host`     | OMERO server address                 |
| `Username` | OMERO username                       |
| `Password` | OMERO password (not persisted)       |

### Swing UI Parameters
| Parameter                        | Description                                                                                  |
|----------------------------------|----------------------------------------------------------------------------------------------|
| **Folder(s) to upload**          | Local folder(s) whose images (including subfolders) will be uploaded                         |
| **Image format**                 | Select *OME-TIFF* or *Other formats*                                                         |
| **OME-TIFF type** *(if OME-TIFF)*| *Standalone* (single file) or *Fileset* (multi-file fileset; only first file is submitted)   |
| **Project**                      | Choose an *existing* OMERO project (dropdown) or create a *new* one (enter a name)          |
| **Dataset**                      | Choose an *existing* dataset (dropdown), create a *new* one (enter a name), or use *New from folder* (dataset named after the selected folder) |
| **Image tags**                   | Parse the image filename (split on `_`) to generate tags                                     |
| **Serie tags**                   | Parse the serie name (split on `_`, space, `,`) to generate tags                            |
| **Folder tags**                  | Use the folder and subfolder names as tags (no parsing)                                      |

> The **Next** button allows configuring multiple folder/dataset uploads in one run before clicking **Finish**.

---

## Outputs

| Output | Description |
|--------|-------------|
| **Images on OMERO** | Images uploaded into the selected/created OMERO dataset |
| **Tags on OMERO** | Tags parsed from image name, serie name, and/or folder hierarchy, linked to each image |
| **CSV report** | Summary of all uploads (image name, path, OMERO ID, tags, status…) saved in `~/Downloads/` |
| **Log file** | Fiji log window saved as a `.txt` file alongside the CSV report |


# Batch Upload FV4000 Tiling Acquisitions to OMERO

## Description
This Fiji script batch uploads tiling acquisitions from an **Olympus FV4000 microscope** to OMERO. For each selected tiling folder, the script can upload individual tile images (`.oir`), the stitched image (`.oir`), and/or acquisition info files (`.omp2info`) as attachments to the dataset. Stitched images and info files are automatically renamed on OMERO to include the parent folder name as a prefix. Optionally, tags can be linked to uploaded images. A CSV report and a Fiji log file are saved upon completion.

> The **Next** button allows configuring multiple tiling folder uploads into different projects/datasets in a single run before clicking **Finish**.

---

## Inputs

### SciJava Parameters
| Parameter  | Description                    |
|------------|--------------------------------|
| `Host`     | OMERO server address           |
| `Username` | OMERO username                 |
| `Password` | OMERO password (not persisted) |

### Swing UI Parameters
| Parameter                    | Description                                                                                                   |
|------------------------------|---------------------------------------------------------------------------------------------------------------|
| **Tiling Folder(s)**         | Local folder(s) containing the FV4000 tiling acquisition files (multiple selection supported)                |
| **Individual tiles**         | If checked, uploads individual tile images (`.oir` files matching the tile naming pattern)                   |
| **Stitch image**             | If checked, uploads the stitched image (`.oir` file matching the stitch naming pattern), renamed with folder prefix |
| **Info files as attachment** | If checked, uploads `.omp2info` acquisition info files as attachments to the dataset, renamed with folder prefix |
| **Project**                  | Choose an *existing* OMERO project (dropdown) or create a *new* one (enter a name)                          |
| **Dataset**                  | Choose an *existing* dataset (dropdown), create a *new* one (enter a name), or use *New from folder* (dataset named after the tiling folder) |
| **Tile/stitch tag**          | If checked, tags each image with its type (`tile` or `stitch`)                                               |
| **Tiling folder tag**        | If checked, tags each image with the name of its parent tiling folder                                        |

---

## Outputs

| Output | Description |
|--------|-------------|
| **Images on OMERO** | Tile and/or stitch images uploaded into the selected/created OMERO dataset |
| **Attachments on OMERO** | `.omp2info` info files attached to the dataset, renamed with the tiling folder name as prefix |
| **Tags on OMERO** | Tags linked to uploaded images based on image type and/or tiling folder name |
| **CSV report** | Summary per image: parent folder, project, dataset, image name/path/ID, type, upload status, tags, renaming info, and attachments — saved in `~/Downloads/` |
| **Log file** | Fiji log window saved as a `.txt` file alongside the CSV report |




# Delete Orphaned Annotations on OMERO

## Description
This Fiji script identifies and optionally deletes **orphaned annotations** from your OMERO account/group. An orphaned annotation is an annotation (tag, file, key-value, comment, or rating) that is no longer linked to any object and therefore no longer accessible from the OMERO webclient. The script can run in report-only mode (safe preview) or report-and-delete mode. Group owners can manage orphaned annotations across their entire group.

> **Note:** By default, the script runs in **"Report only"** mode — nothing is deleted until explicitly switched to **"Report and delete"**.

---

## Inputs

### SciJava Parameters
| Parameter           | Description                                                                                         |
|---------------------|-----------------------------------------------------------------------------------------------------|
| `Host`              | OMERO server address                                                                                |
| `Username`          | OMERO username                                                                                      |
| `Password`          | OMERO password (not persisted)                                                                      |
| `Option`            | `Report only` (default): only generates the CSV report without deleting anything. `Report and delete`: finds and permanently deletes all orphaned annotations of the selected types |
| `Files`             | Include orphaned file attachments                                                                   |
| `Tags`              | Include orphaned tags                                                                               |
| `Key-Values`        | Include orphaned key-value pairs                                                                    |
| `Comments`          | Include orphaned comments                                                                           |
| `Ratings`           | Include orphaned ratings                                                                            |

---

## Outputs

| Output | Description |
|--------|-------------|
| **Deleted annotations on OMERO** | Orphaned annotations of selected types permanently removed *(only in "Report and delete" mode)* |
| **CSV report** | List of all detected orphaned annotations with their type, name, ID, format, size (for files), owner, group and deletion status — saved in `~/Downloads/` |
| **Log file** | Fiji log window saved as a `.txt` file alongside the CSV report |


# Delete Unlinked File Attachments on OMERO

## Description
This Fiji script identifies and optionally deletes **orphaned file attachments** from your OMERO account or group. An orphaned attachment is a file that is not linked to any image or container on OMERO. The script separates all attachments into orphaned and linked files, and generates a full CSV report of both categories. Group owners can extend the search to all files within their group.

> **Note:** Deletion only occurs if the **"Delete unlinked files"** checkbox is explicitly enabled. By default, the script only reports without deleting anything.

---

## Inputs

### SciJava Parameters
| Parameter               | Description                                                                                          |
|-------------------------|------------------------------------------------------------------------------------------------------|
| `Host`                  | OMERO server address                                                                                 |
| `Username`              | OMERO username                                                                                       |
| `Password`              | OMERO password (not persisted)                                                                       |
| `Delete unlinked files` | If checked, permanently deletes all detected orphaned attachments. Default: unchecked (report only) |
| `Choice`                | `Only my files`: searches attachments owned by the logged-in user only. `All files within my group`: searches all attachments across the current group *(group owners only)* |

---

## Outputs

| Output | Description |
|--------|-------------|
| **Deleted attachments on OMERO** | Orphaned file attachments permanently removed *(only if "Delete unlinked files" is checked)* |
| **CSV report** | Full list of all attachments (orphaned and linked), with file name, ID, format, size, owner, group, linked parent objects, and deletion status — saved in `~/Downloads/` |
| **Log file** | Fiji log window saved as a `.txt` file alongside the CSV report |



# Download Images from OMERO Containers

## Description
This Fiji script downloads all images from a specified OMERO container (image, dataset, project, plate, or screen) to a local folder. The local folder hierarchy mirrors the OMERO container structure, with each folder named after its corresponding container following the convention `containerName_containerId`. Fileset images (i.e. multi-file images sharing the same fileset) are automatically handled to avoid duplicate downloads. A CSV report and a Fiji log file are saved upon completion.

> **Note:** If multiple images on OMERO share the same name, they will overwrite each other locally.

---

## Inputs

### SciJava Parameters
| Parameter              | Description                                                                                              |
|------------------------|----------------------------------------------------------------------------------------------------------|
| `Host`                 | OMERO server address                                                                                     |
| `Username`             | OMERO username                                                                                           |
| `Password`             | OMERO password (not persisted)                                                                           |
| `Object to process`    | Type of OMERO container to download from: `image`, `dataset`, `project`, `plate`, or `screen`          |
| `ID`                   | OMERO ID of the selected object                                                                          |
| `Destination folder`   | Local folder where images will be downloaded (subfolders are created automatically per container level) |

---

## Outputs

| Output | Description |
|--------|-------------|
| **Downloaded images** | Original image files saved locally, organised in subfolders mirroring the OMERO container hierarchy (`containerName_containerId`) |
| **CSV report** | Summary per image: OMERO image name, OMERO image ID, fileset ID (if applicable), download status, local destination path, and local filename(s) — saved in `~/Downloads/` |
| **Log file** | Fiji log window saved as a `.txt` file alongside the CSV report |




# Download Images from OMERO Figures

## Description
This Fiji script downloads the original images referenced in one or more **OMERO figures** (created with the OMERO.figure tool) to a local folder. For each selected figure, the script retrieves the figure's JSON file from OMERO, extracts all image IDs referenced in it, and downloads the corresponding images locally. Each figure's images are saved in a dedicated subfolder named after the figure. A CSV report and a Fiji log file are saved upon completion.

---

## Inputs

### SciJava Parameters
| Parameter  | Description                    |
|------------|--------------------------------|
| `Host`     | OMERO server address           |
| `Username` | OMERO username                 |
| `Password` | OMERO password (not persisted) |

### Swing UI Parameters
| Parameter        | Description                                                                                   |
|------------------|-----------------------------------------------------------------------------------------------|
| **Saving folder** | Local destination folder where images will be downloaded (one subfolder created per figure) |
| **Figure**        | Dropdown list of all OMERO figures available to the logged-in user                          |

> The **Next** button allows queuing multiple figure downloads (each with its own saving folder) before clicking **Finish**.

---

## Outputs

| Output | Description |
|--------|-------------|
| **Downloaded images** | Original images referenced in the selected figure(s), saved locally in a subfolder named after each figure |
| **CSV report** | Summary per figure: saving folder, figure name, figure ID, successfully downloaded image IDs, failed image IDs, and status — saved in `~/Downloads/` |
| **Log file** | Fiji log window saved as a `.txt` file alongside the CSV report |


# Extract Channel Names from OMERO Images

## Description
This Fiji script loops over all images within a specified OMERO container (image, dataset, project, well, plate, or screen) and extracts the **channel names** of each image. The results are compiled into a CSV file saved in the Downloads folder. A Fiji log file is also saved upon completion.

---

## Inputs

### SciJava Parameters
| Parameter           | Description                                                                                        |
|---------------------|----------------------------------------------------------------------------------------------------|
| `Host`              | OMERO server address                                                                               |
| `Username`          | OMERO username                                                                                     |
| `Password`          | OMERO password (not persisted)                                                                     |
| `Object to process` | Type of OMERO container to process: `image`, `dataset`, `project`, `well`, `plate`, or `screen`  |
| `Object ID`         | OMERO ID of the selected object                                                                    |

---

## Outputs

| Output | Description |
|--------|-------------|
| **CSV report** | One row per channel per image, containing image name, image ID, and channels name — saved in `~/Downloads/` |
| **Log file** | Fiji log window saved as a `.txt` file alongside the CSV report |


# List Images and Hierarchy from OMERO Containers

## Description
This Fiji script loops over all images within a specified OMERO container (image, dataset, project, well, plate, or screen) and generates a CSV file listing each image with its full **container hierarchy** (dataset/project or well/plate/screen). Optionally, the container hierarchy can also be saved as **key-value pairs** directly on each image in OMERO. A Fiji log file is saved alongside the CSV report.

> **Note:** Commas in image/container names are automatically replaced by semicolons in the CSV output for compatibility. To restore the original names in Excel, use Find & Replace to substitute `;` back to `,`.

---

## Inputs

### SciJava Parameters
| Parameter             | Description                                                                                        |
|-----------------------|----------------------------------------------------------------------------------------------------|
| `Host`                | OMERO server address                                                                               |
| `Username`            | OMERO username                                                                                     |
| `Password`            | OMERO password (not persisted)                                                                     |
| `Object to process`   | Type of OMERO container to process: `image`, `dataset`, `project`, `well`, `plate`, or `screen`  |
| `Object ID`           | OMERO ID of the selected object                                                                    |
| `Save as key-values`  | If checked, saves the container hierarchy (dataset/project or well/plate/screen names) as key-value pairs on each image in OMERO |

---

## Outputs

| Output | Description |
|--------|-------------|
| **Key-values on OMERO** | Container hierarchy (dataset/project or well/plate/screen) attached to each image as key-value pairs *(only if "Save as key-values" is checked)* |
| **CSV report** | One row per image with image name, image ID, and parent container names (dataset, project, well, plate, screen where applicable) — saved in `~/Downloads/` |
| **Log file** | Fiji log window saved as a `.txt` file alongside the CSV report |


# Replace and Clean Tags on OMERO

## Description
This Fiji script finds all occurrences of a specified tag within an OMERO group and replaces it with a new tag across all linked objects (images, datasets, projects, wells, plates, screens, plate acquisitions, and folders). Three operating modes are available, from report-only to full replacement and deletion. The script supports case-insensitive matching to handle tag name variations in a single run. A CSV report and a Fiji log file are saved upon completion.

> **Note:** By default, the script runs in **"Do nothing"** mode — no changes are made to OMERO until a replacement mode is explicitly selected.

---

## Inputs

### SciJava Parameters
| Parameter               | Description                                                                                                                                                                                                      |
|-------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Host`                  | OMERO server address                                                                                                                                                                                             |
| `Username`              | OMERO username                                                                                                                                                                                                   |
| `Password`              | OMERO password (not persisted)                                                                                                                                                                                   |
| `OMERO group`           | Name or ID of the OMERO group to process. Use `default` to use your default group                                                                                                                               |
| `Name of the tag to replace` | Name of the existing tag to find and replace                                                                                                                                                               |
| `Case insensitive?`     | If checked, matches the tag regardless of case (e.g. `DAPI`, `dapi`, `dApi` are all matched)                                                                                                                   |
| `Name of the new tag`   | Name of the replacement tag to link to all objects previously linked to the old tag                                                                                                                             |
| `Mode`                  | `Do nothing`: generates the report only, no changes made. `Replace only`: links the new tag and unlinks the old one, but does not delete the old tag. `Replace and delete`: links the new tag, unlinks and deletes the old tag |

---

## Outputs

| Output | Description |
|--------|-------------|
| **Updated tags on OMERO** | New tag linked to all objects previously linked to the old tag; old tag unlinked *(only in "Replace only" or "Replace and delete" mode)* |
| **Deleted tag on OMERO** | Old tag permanently deleted from OMERO *(only in "Replace and delete" mode)* |
| **CSV report** | Summary per object: object type, ID, name, owner, old tag, new tag, and status of each operation (new tag added, old tag unlinked, old tag deleted) — saved in `~/Downloads/` |
| **Log file** | Fiji log window saved as a `.txt` file alongside the CSV report |


# Transfer Annotations Between OMERO Images

## Description
This Fiji script transfers annotations from **source images** to **target images** across two OMERO datasets, based on a user-provided CSV mapping file. For each source/target image pair defined in the CSV, the script can transfer any combination of tags, key-value pairs, ROIs, attachments, comments, ratings, image description, and channel names. Optionally, annotations from the source dataset itself can also be transferred to the target dataset. A two-part CSV report (transfer status + content) and a Fiji log file are saved upon completion.

> **CSV format:** One row per image pair, with the source image name in the **first column** and the target image name in the **second column**. An optional header line can be included.

---

## Inputs

### SciJava Parameters
| Parameter                        | Description                                                                                                  |
|----------------------------------|--------------------------------------------------------------------------------------------------------------|
| `Host`                           | OMERO server address                                                                                         |
| `Username`                       | OMERO username                                                                                               |
| `Password`                       | OMERO password (not persisted)                                                                               |
| `Source dataset ID`              | OMERO ID of the dataset containing the images to copy annotations **from**                                  |
| `Target dataset ID`              | OMERO ID of the dataset containing the images to copy annotations **to**                                    |
| `CSV table`                      | Local CSV file mapping source image names (column 1) to target image names (column 2)                      |
| `My file has a header`           | If checked, the first line of the CSV file is treated as a header and skipped                               |
| `Include transfer on source dataset` | If checked, also transfers selected annotations from the source dataset object to the target dataset    |
| `Tags`                           | If checked, transfers tags                                                                                   |
| `Key-value pairs`                | If checked, transfers key-value pairs                                                                        |
| `ROIs`                           | If checked, transfers ROIs *(images only)*                                                                   |
| `Attachments`                    | If checked, transfers file attachments                                                                       |
| `Comments`                       | If checked, transfers comments                                                                               |
| `Ratings`                        | If checked, transfers ratings                                                                                |
| `Description`                    | If checked, transfers the image description                                                                  |
| `Channels name`                  | If checked, transfers channel names *(images only; requires matching channel count)*                        |

---

## Outputs

| Output | Description |
|--------|-------------|
| **Annotations on OMERO** | Selected annotation types transferred from each source image/dataset to its corresponding target image/dataset |
| **CSV report** | Two-part summary: (1) transfer status per annotation type per image pair (Transferred / Skipped / Failed / None), (2) content of what was transferred — saved in `~/Downloads/` |
| **Log file** | Fiji log window saved as a `.txt` file alongside the CSV report |



# Z-Projection of OMERO Images

## Description
This Fiji script performs a **Z-projection** (max or min intensity) on images stored on OMERO. Images can be processed individually or in batch from a dataset or project. The projected image is saved locally as a pyramidal OME-TIFF (via Kheops), optionally sent back to OMERO in the same dataset as the source image, and automatically annotated with a tag and key-value pairs describing the projection parameters. The script accepts either direct OMERO object IDs or OMERO webclient URLs as input.

> If the source image belongs to a plate/screen rather than a dataset, a new dataset named `<projectionType>_projections` is automatically created to store the result.

---

## Inputs

### SciJava Parameters
| Parameter                 | Description                                                                                                          |
|---------------------------|----------------------------------------------------------------------------------------------------------------------|
| `Host`                    | OMERO server address                                                                                                 |
| `Username`                | OMERO username                                                                                                       |
| `Password`                | OMERO password (not persisted)                                                                                       |
| `Object to process`       | Type of OMERO container: `image`, `dataset`, or `project`                                                           |
| `Object ID or object URL` | OMERO ID of the object to process, or a valid OMERO webclient URL (supports multiple IDs via URL)                   |
| `Full stack projection`   | If checked, projects the full Z-stack (overrides Start Z / End Z)                                                   |
| `Start Z`                 | First Z-slice to include in the projection (set to `-1` to use the first slice)                                     |
| `End Z`                   | Last Z-slice to include in the projection (set to `-1` to use the last slice)                                       |
| `Projection type`         | Type of intensity projection: `max` (maximum) or `min` (minimum)                                                    |
| `Temporary saving folder` | Local folder used to temporarily save the projected OME-TIFF before upload; defaults to `~/Downloads/` if not set  |
| `Send projection`         | If checked, uploads the projected image back to OMERO in the source dataset                                         |
| `Show image`              | If checked, displays the source image in Fiji during processing                                                      |

---

## Outputs

| Output | Description |
|--------|-------------|
| **Projected image on OMERO** | Pyramidal OME-TIFF of the Z-projection uploaded to the same dataset as the source image *(only if "Send projection" is checked)* |
| **Tag on OMERO** | A tag `<projectionType>_projection` linked to the projected image |
| **Key-values on OMERO** | Key-value pairs attached to the projected image: source image ID and name, projection type, and Z-slice range used |
