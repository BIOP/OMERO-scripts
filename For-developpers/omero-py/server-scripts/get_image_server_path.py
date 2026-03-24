import shutil
import pyminizip
import omero.scripts as scripts
from omero.gateway import BlitzGateway
from omero.rtypes import rlong, rstring
import os
import omero

P_DATA_TYPE = "Data_Type"
P_IDS = "IDs"
P_ZIP_NAME = "Zip name"
P_PASSWORD = "Password"
P_ATT = "Download attachments for all images"

# root HRM path
root = "/mnt/hrmshare"  # SV-OPEN folder

# temporary folder to add the attachments before zipping
# need to be in the destination folder because permission denied
tmp_path = f"{root}/tmpDownloads/"


def prepare_download(conn, fs_path_dict, att_path_dict, fs_prefix_dict):
    resources = conn.c.sf.sharedResources()
    repos = resources.repositories()
    managed_repo_dir = ""
    fs_path_list = []
    fs_prefix_list = []

    # get the managedRepository
    for desc in repos.descriptions:
        if desc.name.val == "ManagedRepository":
            managed_repo_dir = desc.path.val + desc.name.val

    if managed_repo_dir != "":
        for fs_id, fs_paths in fs_path_dict.items():
            fs_prefix = fs_prefix_dict[fs_id]

            # adding images
            for fs_path in fs_paths:
                fs_path_list.append(managed_repo_dir + "/" + fs_path)
                fs_prefix_list.append(fs_prefix)

            # adding attachments
            if fs_id in att_path_dict.keys():
                att_path_list = att_path_dict[fs_id]
                for att_path in att_path_list:
                    fs_path_list.append(att_path)
                    fs_prefix_list.append(fs_prefix)
    else:
        print("No managed repository found. Cannot create the zip file.")

    return fs_path_list, fs_prefix_list


def process_image(image, parent_prefix, fs_path_dict, att_path_dict, fs_prefix_dict, download_attachments):
    """
    Download the image
    return 1 if owner has been added, 0 otherwise
    """

    # Download the files composing the image
    fs = image.getFileset()

    if not fs:
        print("ERROR", f"ERROR: no original file(s) for [%s] found!" % image.getId())

    fs_id = fs.getId()
    if fs_id in fs_path_dict:
        print("WARNING", f"Image part of the same fileset %s! Skipping..." % fs_id)
    else:
        fs_path_dict[fs_id] = []
        fileset_prefix = parent_prefix[:]
        fileset_prefix.append(f"Fileset_{fs_id}")

        fs_prefix_dict[fs_id] = "/".join(fileset_prefix)
        for file_wrapper in fs.listFiles():
            fs_path_dict[fs_id].append(file_wrapper.getPath() + file_wrapper.getName())

        if download_attachments:
            att_path_dict[fs_id] = []

            # create tmp folder
            if not os.path.exists(tmp_path):
                os.makedirs(tmp_path)

            # get all attachments from the entire fileset i.e. all images linked to the current fileset
            for linked_image in fs.copyImages():
                for ann in linked_image.listAnnotations():
                    if ann.OMERO_TYPE == omero.model.FileAnnotationI:
                        file_path = os.path.join(tmp_path, f"{ann.getFile().getId()}_{ann.getFile().getName()}")
                        if not os.path.exists(file_path):
                            try:
                                copy_attachment(file_path, ann)
                                att_path_dict[fs_id].append(file_path)
                            except Exception as e:
                                print(f"ERROR: cannot copy attachment for image {linked_image.getId()}: {e}")


def copy_attachment(file_path, ann):
    with open(str(file_path), 'wb') as f:
        print("Copying file to", file_path, "...")
        for chunk in ann.getFileInChunks():
            f.write(chunk)


def process_dataset(dataset, parent_prefix, fs_path_dict, att_path_dict, fs_prefix_dict, download_attachments):
    """
    Download all images within the given dataset
    return the number of processed images
    """
    dataset_prefix = parent_prefix[:]
    dataset_prefix = dataset_prefix.append(f"Dataset_{dataset.getName()}_{dataset.getId()}")
    for image in dataset.listChildren():
       process_image(image, dataset_prefix, fs_path_dict, att_path_dict, fs_prefix_dict, download_attachments)


def process_project(project, parent_prefix, fs_path_dict, att_path_dict, fs_prefix_dict, download_attachments):
    """
    Download all images within the given project
    return the number of processed images & datasets
    """
    project_prefix = parent_prefix[:]
    project_prefix = project_prefix.append(f"Project_{project.getName()}_{project.getId()}")
    for dataset in project.listChildren():
        process_dataset(dataset, project_prefix, fs_path_dict, att_path_dict, fs_prefix_dict, download_attachments)


def download_and_zip_images(conn, script_params):
    """
    Get the given container(s) or given experimenter(s) and scan all their children to add
    data owner as a key-value pair to all of them.
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

    fs_path_dict = {}
    fs_prefix_dict = {}
    att_path_dict = {}
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

        zip_name_tmp = zip_name
        i = 1
        while os.path.exists(f"{zip_path}/{zip_name_tmp}.zip"):
            zip_name_tmp = f"{zip_name}_{i}"
            i = i + 1
        zip_path = f"{zip_path}/{zip_name_tmp}.zip"

        # check if the user has an HRM account (a folder with his/her name should already exist)
        for object_id in object_id_list:

            # search in all the user's group
            # conn.SERVICE_OPTS.setOmeroGroup('-1')

            # get the object
            omero_object = conn.getObject(object_type, object_id)

            # check if that object exists
            if omero_object is not None:
                # set the correct group id
                # conn.SERVICE_OPTS.setOmeroGroup(omero_object.getDetails().getGroup().getId())

                # select object type and add owner as key-value pair
                parent_prefix = []
                if object_type == 'Image':
                    process_image(omero_object, parent_prefix, fs_path_dict, att_path_dict, fs_prefix_dict, dwnld_atts)
                if object_type == 'Dataset':
                    process_dataset(omero_object, parent_prefix, fs_path_dict, att_path_dict, fs_prefix_dict, dwnld_atts)
                if object_type == 'Project':
                    process_project(omero_object, parent_prefix, fs_path_dict, att_path_dict, fs_prefix_dict, dwnld_atts)

            else:
                print(object_type, object_id, "does not exist or you do not have access to it")

        if len(fs_path_dict) > 0 and len(fs_prefix_dict) > 0:
            fs_path_list, fs_prefix_list = prepare_download(conn, fs_path_dict, att_path_dict, fs_prefix_dict)
            try:
                pyminizip.compress_multiple(fs_path_list, fs_prefix_list, f"{zip_path}", password, 1)
                message = f"Zip file created and accessible under https://sv-open.epfl.ch/ptbiop-public{zip_path.replace(root, '')}"
            except Exception as e:
                message = "ERROR: cannot zip the files"
                err = message
                print(f"{message}: {e}")
            finally:
                if os.path.exists(tmp_path):
                    shutil.rmtree(tmp_path)
    else:
        message = "The folder 'https://sv-open.epfl.ch/ptbiop-public' doesn't exist. Cannot download objects."
        err = message
        print(message)

    return message, err


def run_script():
    data_types = [rstring("Project"), rstring("Dataset"), rstring("Image"),
                  rstring("Screen"), rstring("Plate"), rstring("Well")]

    client = scripts.client(
        'Duplicate images',
        """
    This script duplicates n times all images under the selected container(s). If a container if selected, 
    the container itself, as well as its children, are also duplicated.
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
            description="Download all attachments linked to selected images",
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