"""
 Duplicate_images.py
 Duplicate selected images and move them to the specified dataset
-----------------------------------------------------------------------------
  Copyright (C) 2023
  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.
  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.
  You should have received a copy of the GNU General Public License along
  with this program; if not, write to the Free Software Foundation, Inc.,
  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
------------------------------------------------------------------------------
Created by Rémy Dornier
"""


import omero
import omero.scripts as scripts
import omero.model as model
from omero.gateway import BlitzGateway
from omero.gateway import DatasetWrapper
from omero.gateway import MapAnnotationWrapper
from omero.plugins.sessions import SessionsControl
from omero.plugins.duplicate import DuplicateControl
from omero.cli import CLI
from omero.rtypes import rlong, rstring, robject
from datetime import datetime

P_DATA_TYPE = "Data_Type"
P_IDS = "IDs"
P_DUP_NUM = "Number of duplicate to create"
P_PROJECT = "Target project"
P_DATASET = "Dataset"

OMERO_SERVER = "omero-server.epfl.ch"
OMERO_WEBSERVER = "omero.epfl.ch"
PORT = "4064"


def add_annotation_key_value(conn, target_obj, annotations):
    """Add a key-value "map" annotation to an OMERO object.
    Parameters
    ----------
    conn : omero.gateway.BlitzGateway
        The OMERO connection object.
    target_obj : omero.gateway.BlitzObjectWrapper
        The image to attach the KVP to
    annotations : list[list]
        A dictionary of the duplicate information
    Returns
    -------
    bool
        True in case of success, False otherwise.
    Raises
    ------
    RuntimeError
        Raised in case re-establishing the OMERO connection fails.
    """

    if target_obj is None:
        print("ERROR", f"Unable to identify target object in OMERO!")
        return False

    namespace = f"omero.duplicate"
    map_ann = MapAnnotationWrapper(conn)
    map_ann.setValue(annotations)
    map_ann.setNs(namespace)
    map_ann.save()
    target_obj.linkAnnotation(map_ann)

    print("SUCCESS", f"Added annotation to {target_obj.getId()} : {annotations}")

    return True


# This method is taken from ezomero project :
# https://github.com/TheJacksonLaboratory/ezomero/blob/main/ezomero/_posts.py#L377
def create_dataset(conn, dataset_name, description=None):
    """Create a new dataset.
    Parameters
    ----------
    conn : ``omero.gateway.BlitzGateway`` object
        OMERO connection.
    dataset_name : str
        Name of the new object to be created.
    description : str, optional
        Description for the new Dataset.
    Returns
    -------
    dataset_id : int
        Id of the new Dataset.
    Notes
    -----
    Dataset will be created in the Group specified in the connection. Group can
    be changed using ``conn.SERVICE_OPTS.setOmeroGroup``.
    Examples
    --------
    >>> dataset_id = create_dataset(conn, "My New Dataset")
    >>> print(dataset_id)
    238
    """
    if type(dataset_name) is not str:
        raise TypeError('Dataset name must be a string')

    if type(description) is not str and description is not None:
        raise TypeError('Dataset description must be a string')

    dataset = DatasetWrapper(conn, model.DatasetI())
    dataset.setName(dataset_name)
    if description is not None:
        dataset.setDescription(description)
    dataset.save()

    return dataset.getId()


def duplicate_images(conn: BlitzGateway, script_params):
    """Duplicate selected images
    Parameters
    ----------
    conn : ``omero.gateway.BlitzGateway`` object
        OMERO connection.
    script_params : dict
        User defined parameters
    Returns
    -------
    message : str
        Informative message for the user.
    dataset : omero.gateway.BlitzObject
        the created dataset
    err: Exception
        In case an error was caught
    """

    # Image ids
    image_ids = script_params[P_IDS]

    # target dataset
    dataset_base_name = script_params[P_DATASET]
    dataset = None

    # Number of image copy
    n_iter = script_params[P_DUP_NUM]

    # Build the duplicate command
    str_ids = [str(img_id) for img_id in image_ids]
    import_args = ["duplicate",
                   f"Image:{','.join(str_ids)}"
                   ]

    for copy_id in range(n_iter):
        # open cli connection
        with omero.cli.cli_login("-k", "%s" % conn.c.getSessionId(), "-s", OMERO_SERVER, "-p", PORT) as cli:
            cli.register('duplicate', DuplicateControl, '_')
            cli.register('sessions', SessionsControl, '_')
            # launch duplication
            try:
                cli.invoke(import_args, strict=True)
                print("SUCCESS", f"Duplicated images {str_ids}")
            except PermissionError as err:
                message = f"Error during duplication of images {str_ids}: {err}"
                cli.get_client().closeSession()
                cli.close()
                return message, None, err
    
            cli.get_client().closeSession()
    
        # create the target dataset
        dataset_name = f"{dataset_base_name}_{copy_id}"
        dataset_id = create_dataset(conn, dataset_name)
        dataset = conn.getObject("Dataset", dataset_id)
        dataset_kvps = [
            ["Source images",
             f"https://{OMERO_WEBSERVER}/webclient/?show={'|'.join([f'image-{im_id}' for im_id in str_ids])}"]
        ]
        add_annotation_key_value(conn, dataset, dataset_kvps)
    
        # get the duplicated images from the orphaned folder
        duplicated_images = conn.getObjects("Image", opts={'orphaned': True})
    
        img_kvps = [
            ["Duplicated by", conn.getUser().getFullName()],
            ["Duplication date", datetime.now().astimezone().strftime("%Y-%m-%d %H:%M:%S")]]
    
        # add kvps & move orphaned images to the right dataset
        for dup_img in duplicated_images:
            add_annotation_key_value(conn, dup_img, img_kvps)
            link = omero.model.DatasetImageLinkI()
            link.setChild(omero.model.ImageI(dup_img.id, False))
            link.setParent(omero.model.DatasetI(dataset_id, False))
            conn.getUpdateService().saveObject(link)

    return f"Successful transfer of images {str_ids} to dataset(s) '{dataset_base_name}'", dataset, None


def run_script():
    data_types = [rstring('Image')]

    client = scripts.client(
        'Duplicate images',
        """
    This script duplicates n times the selected images and move them in the specified dataset.
        """,
        scripts.String(
            P_DATA_TYPE, optional=False, grouping="1",
            description="Choose source of images (only Images supported)",
            values=data_types, default="Image"),

        scripts.List(
            P_IDS,  optional=False, grouping="2",
            description="List of Images IDs to duplicate").ofType(rlong(0)),
        scripts.Int(
            P_DUP_NUM, optional=False, grouping="3",
            description="How many copies of the selected images do you want to create ?", default=1, min=1),

        scripts.String(
            P_DATASET, optional=True, grouping="4",
            description="ONLY FOR IMAGES. New dataset to create. Leave blank to copy images in orphaned folder.",
            default=""),

        authors=["Rémy Dornier"],
        institutions=["EPFL - BIOP"],
        contact="omero@groupes.epfl.ch"
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
        message, res_obj, err = duplicate_images(conn, script_params)
        client.setOutput("Message", rstring(message))
        if res_obj is not None:
            client.setOutput("Result", robject(res_obj._obj))
        if err is not None:
            client.setOutput("ERROR", rstring(err))

    except AssertionError as err:
        client.setOutput("ERROR", rstring(err))
        raise AssertionError(str(err))
    finally:
        client.closeSession()


if __name__ == "__main__":
    run_script()
