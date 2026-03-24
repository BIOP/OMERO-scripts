"""
 Download_protected_zip.py
 Download a password-protected zip file of selected objects
 -----------------------------------------------------------------------------
 MIT License

 Copyright (c) 2026 ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP)

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all
 copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 SOFTWARE.
 ------------------------------------------------------------------------------
 Created by Rémy Dornier

"""

import shutil
import pyminizip
import omero.scripts as scripts
from omero.gateway import BlitzGateway
from omero.rtypes import rlong, rstring
import os
import omero

# constants for the UI
P_DATA_TYPE = "Data_Type"
P_IDS = "IDs"
P_ZIP_NAME = "Zip name"
P_PASSWORD = "Password"
P_ATT = "Download attachments for all objects"

# root SV-OPEN path
root = "/mnt/svopen"

# temporary folder to add the attachments before zipping
# need to be in the destination folder because permission denied on the server itself
tmp_path = f"{root}/tmpDownloads/"


def prepare_download(conn, fs_path_dict, att_path_dict, fs_prefix_dict, att_prefix_dict):
    """
    Get the full list of file (images + attachments) path

    Parameters
    ----------
    conn: ``omero.gateway.BlitzGateway`` object
        OMERO connection.
    fs_path_dict: dict
        Dictionary of [fileset_id]:[fileset_server_path]
    att_path_dict: dict
        Dictionary of [att_id]:[attachment_path]
    fs_prefix_dict: dict
        Dictionary of [fileset_id]:[inner_zip_hierarchy_path]
    att_prefix_dict: dict
        Dictionary of [att_id]:[inner_zip_hierarchy_path]

    Returns
    -------
    fs_path_list: list
        all file paths, images and attachments
    fs_prefix_list: list
        all inner zip hierarchy path, corresponding one by one to the file paths
    """
    fs_path_list = []
    fs_prefix_list = []

    # get the managedRepository
    resources = conn.c.sf.sharedResources()
    repos = resources.repositories()
    managed_repo_dir = ""
    for desc in repos.descriptions:
        if desc.name.val == "ManagedRepository":
            managed_repo_dir = desc.path.val + desc.name.val

    if managed_repo_dir != "":
        # adding images
        for fs_id, fs_paths in fs_path_dict.items():
            fs_prefix = fs_prefix_dict[fs_id]
            for fs_path in fs_paths:
                fs_path_list.append(managed_repo_dir + "/" + fs_path)
                fs_prefix_list.append(fs_prefix)

        # adding attachments
        for fs_id, att_paths in att_path_dict.items():
            att_prefix = att_prefix_dict[fs_id]
            for att_path in att_paths:
                fs_path_list.append(att_path)
                fs_prefix_list.append(att_prefix)
    else:
        print("No managed repository found. Cannot create the zip file.")

    return fs_path_list, fs_prefix_list


def copy_attachment(file_path, ann):
    """
    Do a hard copy of the current file

    Parameters
    ----------
    file_path: str
        absolute path of the annotation file to copy
    ann: omero.model.Annotation
        file to copy

    Returns
    -------

    """
    with open(str(file_path), 'wb') as f:
        print("Copying file to", file_path, "...")
        for chunk in ann.getFileInChunks():
            f.write(chunk)


def process_attachment(container, att_path_dict, att_id):
    """
    Do a hard copy of all the attachments linked to the current object

    Parameters
    ----------
    container: omero.model.Object
        The object to get attachments from
    att_path_dict: dict
        Dictionary of [att_id]:[attachment_path]
    att_id: int
        attachment id linked to the current fileset

    Returns
    -------

    """
    for ann in container.listAnnotations():
        # only process attachments, not other types of annotations
        if ann.OMERO_TYPE == omero.model.FileAnnotationI:
            file_path = os.path.join(tmp_path, f"{ann.getFile().getId()}_{ann.getFile().getName()}")
            if not os.path.exists(file_path):
                try:
                    # do a hard copy of the attachment, with right name & extension
                    # i.e. human-readable
                    copy_attachment(file_path, ann)
                    att_path_dict[att_id].append(file_path)
                except Exception as e:
                    print(f"ERROR: cannot copy attachment for {container.OMERO_CLASS} {container.getId()}: {e}")


def process_image(image, parent_prefix, fs_path_dict, att_path_dict, fs_prefix_dict, att_prefix_dict, download_attachments):
    """
    get the all image(s) server path coming from the same current fileset, including attachments

    Parameters
    ----------
    image: omero.model.Image object
        The image to process
    parent_prefix: list
        folder names under which the images will be downloaded (inner zip hierarchy)
    fs_path_dict: dict
        Dictionary of [fileset_id]:[fileset_server_path]
    att_path_dict: dict
        Dictionary of [att_id]:[attachment_path]
    fs_prefix_dict: dict
        Dictionary of [fileset_id]:[inner_zip_hierarchy_path]
    att_prefix_dict: dict
        Dictionary of [att_id]:[inner_zip_hierarchy_path]
    download_attachments: bool
        True to download attachments

    Returns
    -------

    """

    # get the current fileset
    fs = image.getFileset()

    if not fs:
        print("ERROR", f"ERROR: no original file(s) for [%s] found!" % image.getId())

    # only get image paths if the fileset has not been processed yet
    fs_id = fs.getId()
    if fs_id in fs_path_dict:
        print("WARNING", f"Image {image.getId()} part of the same fileset {fs_id}! Skipping...")
    else:
        print(f"Getting server path(s) for fileset {fs_id}...")
        fs_path_dict[fs_id] = []
        fileset_prefix = parent_prefix[:]
        fileset_prefix.append(f"Fileset_{fs_id}")
        fs_prefix_dict[fs_id] = "/".join(fileset_prefix)

        # get paths for all images within the fileset
        for file_wrapper in fs.listFiles():
            fs_path_dict[fs_id].append(file_wrapper.getPath() + file_wrapper.getName())

        if download_attachments:
            att_path_dict[fs_id] = []
            att_prefix_dict[fs_id] = "/".join(fileset_prefix)

            # get all attachments from the entire fileset i.e. all images linked to the current fileset
            for linked_image in fs.copyImages():
                process_attachment(linked_image, att_path_dict, fs_id)


def process_dataset(dataset, parent_prefix, fs_path_dict, att_path_dict, fs_prefix_dict, att_prefix_dict, download_attachments):
    """
    Loop over all images within the current dataset and get their server path, including attachments

    Parameters
    ----------
    dataset: omero.model.Dataset object
        The dataset to process
    parent_prefix: list
        folder names under which the images will be downloaded (inner zip hierarchy)
    fs_path_dict: dict
        Dictionary of [fileset_id]:[fileset_server_path]
    att_path_dict: dict
        Dictionary of [att_id]:[attachment_path]
    fs_prefix_dict: dict
        Dictionary of [fileset_id]:[inner_zip_hierarchy_path]
    att_prefix_dict: dict
        Dictionary of [att_id]:[inner_zip_hierarchy_path]
    download_attachments: bool
        True to download attachments

    Returns
    -------

    """
    dataset_prefix = parent_prefix[:]
    dataset_prefix.append(f"Dataset_{dataset.getName()}_{dataset.getId()}")

    # get attachments paths
    if download_attachments:
        att_id = f"d{dataset.getId()}"
        att_path_dict[att_id] = []
        att_prefix_dict[att_id] = "/".join(dataset_prefix)
        process_attachment(dataset, att_path_dict, att_id)

    for image in dataset.listChildren():
       process_image(image, dataset_prefix, fs_path_dict, att_path_dict, fs_prefix_dict, att_prefix_dict, download_attachments)


def process_project(project, parent_prefix, fs_path_dict, att_path_dict, fs_prefix_dict, att_prefix_dict, download_attachments):
    """
    Loop over all datasets within the current project and get their server path, including attachments

    Parameters
    ----------
    project: omero.model.Project object
        The project to process
    parent_prefix: list
        folder names under which the images will be downloaded (inner zip hierarchy)
    fs_path_dict: dict
        Dictionary of [fileset_id]:[fileset_server_path]
    att_path_dict: dict
        Dictionary of [att_id]:[attachment_path]
    fs_prefix_dict: dict
        Dictionary of [fileset_id]:[inner_zip_hierarchy_path]
    att_prefix_dict: dict
        Dictionary of [att_id]:[inner_zip_hierarchy_path]
    download_attachments: bool
        True to download attachments

    Returns
    -------

    """
    project_prefix = parent_prefix[:]
    project_prefix.append(f"Project_{project.getName()}_{project.getId()}")

    # get attachments paths
    if download_attachments:
        att_id = f"p{project.getId()}"
        att_path_dict[att_id] = []
        att_prefix_dict[att_id] = "/".join(project_prefix)
        process_attachment(project, att_path_dict, att_id)

    for dataset in project.listChildren():
        process_dataset(dataset, project_prefix, fs_path_dict, att_path_dict, fs_prefix_dict, att_prefix_dict, download_attachments)


def download_and_zip_images(conn, script_params):
    """
    main loop to create the zip file, with project/dataset hierarchy, of images and attachments

    Parameters
    ----------
    conn: ``omero.gateway.BlitzGateway`` object
        OMERO connection.
    script_params : dict
        User defined parameters

    Returns
    -------
    message: str
        final message to show to the user
    err: str
        Error message if there is one
    """

    # select the object type (image, dataset, project, well, plate, screen, user)
    object_type = script_params[P_DATA_TYPE]
    # enter its corresponding ID (except for 'user' : enter the username)
    object_id_list = script_params[P_IDS]
    # Zip name
    zip_name = script_params[P_ZIP_NAME]
    # password
    password = script_params[P_PASSWORD]
    # download attachments for all images
    dwnld_atts = script_params[P_ATT]

    # check for valid password
    if password is None or password == "" or password.strip() == "":
        message = "Password should not be empty. Please provide correct password."
        print(message)
        return "", message

    fs_path_dict = {}
    fs_prefix_dict = {}
    att_path_dict = {}
    att_prefix_dict = {}
    message = ""
    err = None

    # check if the root directory exists ==> necessary because sv-nas1 server is mounted on OMERO server
    if os.path.isdir(root):
        default_group_id = conn.getEventContext().groupId
        group = conn.getObject("ExperimenterGroup", default_group_id)

        # create the destination folder
        zip_path = f"{root}/omero/{group.getName()}"
        if not os.path.exists(zip_path):
            os.makedirs(zip_path)

        # check that the zip file doesn't already exist
        # in case it exists, simple add _id
        zip_name_tmp = zip_name
        i = 1
        while os.path.exists(f"{zip_path}/{zip_name_tmp}.zip"):
            zip_name_tmp = f"{zip_name}_{i}"
            i = i + 1
        zip_path = f"{zip_path}/{zip_name_tmp}.zip"
        print(f"Zip file will be created under '{zip_path}'")

        # create tmp folder
        if not os.path.exists(tmp_path):
            os.makedirs(tmp_path)

        for object_id in object_id_list:

            # search in all the user's group
            # conn.SERVICE_OPTS.setOmeroGroup('-1')

            # get the object
            omero_object = conn.getObject(object_type, object_id)

            # check if that object exists
            if omero_object is not None:
                # set the correct group id
                # conn.SERVICE_OPTS.setOmeroGroup(omero_object.getDetails().getGroup().getId())

                parent_prefix = []
                if object_type == 'Image':
                    process_image(omero_object, parent_prefix, fs_path_dict, att_path_dict, fs_prefix_dict, att_prefix_dict, dwnld_atts)
                if object_type == 'Dataset':
                    process_dataset(omero_object, parent_prefix, fs_path_dict, att_path_dict, fs_prefix_dict, att_prefix_dict, dwnld_atts)
                if object_type == 'Project':
                    process_project(omero_object, parent_prefix, fs_path_dict, att_path_dict, fs_prefix_dict, att_prefix_dict, dwnld_atts)

            else:
                print(object_type, object_id, "does not exist or you do not have access to it")

        # only creates zip if there is at least one image
        if len(fs_path_dict) > 0 and len(fs_prefix_dict) > 0:
            # get the full list of file (images + attachments) path
            print("Preparing download...")
            fs_path_list, fs_prefix_list = prepare_download(conn, fs_path_dict, att_path_dict, fs_prefix_dict, att_prefix_dict)

            try:
                # zip it
                print("Creating zip...")
                pyminizip.compress_multiple(fs_path_list, fs_prefix_list, f"{zip_path}", password, 1)
                message = f"Zip file created and accessible under https://sv-open.epfl.ch/ptbiop-public{zip_path.replace(root, '')}"
                print(message)
            except Exception as e:
                message = "ERROR: cannot zip the files"
                err = message
                print(f"{message}: {e}")

        # delete tmp folder
        if os.path.exists(tmp_path):
            shutil.rmtree(tmp_path)
    else:
        message = "The folder 'https://sv-open.epfl.ch/ptbiop-public' doesn't exist. Cannot download objects."
        err = message
        print(message)

    return message, err


def run_script():
    data_types = [rstring("Project"), rstring("Dataset"), rstring("Image")]

    client = scripts.client(
        'Download object(s) as zip',
        """
    This script creates a password-protected zip file with the selected objects, and optionally, the attachments links 
    to the selected objects. The zip is saved under the default location 'https://sv-open.epfl.ch/ptbiop-public/omero', 
    in a folder named with the OMERO group from which the objects are coming from.
    \t 
    WARNING: the location on 'https://sv-open.epfl.ch/' is FULLY PUBLIC, which means that the zip file can be
    downloaded by anyone who has the link. It's therefore important that you provide a strong password to avoid
    any data leak issues.
    \t
    If downloading attachments is selected, all attachments will be downloaded, whatever their extension.
        """,
        scripts.String(
            P_DATA_TYPE, optional=False, grouping="1",
            description="Object to download",
            values=data_types, default="Image"),
        scripts.List(
            P_IDS,  optional=False, grouping="2",
            description="Objects IDs").ofType(rlong(0)),
        scripts.String(
            P_ZIP_NAME, optional=False, grouping="3",
            description="Name of the zip file"),
        scripts.String(
            P_PASSWORD, optional=False, grouping="4",
            description="Password to secure the zip file"),
        scripts.Bool(
            P_ATT, optional=True, grouping="5",
            description="Download all attachments linked to selected objects",
            default=False),

        authors=["Rémy Dornier"],
        institutions=["EPFL - BIOP"],
        contact="omero@groupes.epfl.ch",
        version="1.0.0"
    )

    try:
        # process the list of args above.
        script_params = {}
        for key in client.getInputKeys():
            if client.getInput(key):
                script_params[key] = client.getInput(key, unwrap=True)

        # wrap client to use the Blitz Gateway
        conn = BlitzGateway(client_obj=client)
        print("script params")
        for k, v in script_params.items():
            if not k == P_PASSWORD:
                print(k, v)
        message, err = download_and_zip_images(conn, script_params)
        client.setOutput("Message", rstring(message))
        if err is not None:
            client.setOutput("ERROR", rstring(err))

    except AssertionError as err:
        client.setOutput("ERROR", rstring(err))
        raise AssertionError(str(err))
    finally:
        client.closeSession()


if __name__ == "__main__":
    run_script()