"""
 Share_images_across_groups.py
 Duplicate and transfer images from one group to another
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
from omero.plugins.chgrp import ChgrpControl
from omero.cli import CLI
from omero.rtypes import rlong, rstring, robject
from datetime import datetime

P_DATA_TYPE = "Data_Type"
P_IDS = "IDs"
P_GROUP = "Target group"
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


def duplicate_and_move_to_group(conn: BlitzGateway, script_params):
    """Duplicate selected images and move them to the selected group
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

    # target group
    target_group_name = script_params[P_GROUP]
    current_group = conn.getGroupFromContext()
    excluded_groups = [0, 1, 2]

    # target dataset
    dataset = script_params[P_DATASET]

    # check if the connected user is part of the target group
    available_groups = [g.name.lower() for g in conn.listGroups() if g.id not in excluded_groups]

    if target_group_name.lower() not in available_groups:
        message = f"ERROR : You are not part of the group {target_group_name}. You cannot transfer images to that group"
        return message, None, None

    # Build the duplicate command
    str_ids = [str(img_id) for img_id in image_ids]
    import_args = ["duplicate",
                   f"Image:{','.join(str_ids)}"
                   ]

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
    dataset_id = create_dataset(conn, dataset)
    dataset = conn.getObject("Dataset", dataset_id)
    dataset_kvps = [
        ["Source images",
         f"https://{OMERO_WEBSERVER}/webclient/?show={'|'.join([f'image-{im_id}' for im_id in str_ids])}"]
    ]
    add_annotation_key_value(conn, dataset, dataset_kvps)

    # get the duplicated images from the orphaned folder
    duplicated_images = conn.getObjects("Image", opts={'orphaned': True})

    # get the target group
    target_group = [g for g in conn.listGroups()
                    if g.id not in excluded_groups and g.name.lower() == target_group_name.lower()][0]
    target_group_name = target_group.getName()

    img_kvps = [
        ["Duplicated by", conn.getUser().getFullName()],
        ["Duplication date", datetime.now().astimezone().strftime("%Y-%m-%d %H:%M:%S")],
        ["Base group", current_group.getName()],
        ["Target group", target_group_name]]

    # add kvps & move orphaned images to the right dataset
    for dup_img in duplicated_images:
        add_annotation_key_value(conn, dup_img, img_kvps)
        link = omero.model.DatasetImageLinkI()
        link.setChild(omero.model.ImageI(dup_img.id, False))
        link.setParent(omero.model.DatasetI(dataset_id, False))
        conn.getUpdateService().saveObject(link)

    # Build the chgrp command only of the 2 groups are different
    if current_group.getId() != target_group.getId():
        import_args = ["chgrp",
                       "Group:%s" % target_group.getId(),
                       "Dataset:%s" % dataset_id,
                       "--include", "Image", "Annotation"
                      ]
        # open cli connection
        with omero.cli.cli_login("-k", "%s" % conn.c.getSessionId(), "-s", OMERO_SERVER, "-p", PORT) as cli:
            cli.register('chgrp', ChgrpControl, '_')
            cli.register('sessions', SessionsControl, '_')
            # launch duplication
            try:
                cli.invoke(import_args, strict=True)
                print("SUCCESS", f"Moved from group {current_group.getName()} to group {target_group_name}")
            except PermissionError as err:
                message = f"Error during moving dataset {dataset}:{dataset_id} " \
                          f"from group {current_group.getName()} to group {target_group_name} : {err}"
                cli.get_client().closeSession()
                cli.close()
                return message, dataset, err
            cli.get_client().closeSession()

    return f"Successful transfer of images {str_ids} to group {target_group_name}", dataset, None


def run_script():
    data_types = [rstring('Image')]

    client = scripts.client(
        'Share images across groups',
        """
    This script duplicates selected images in the specified dataset and transfer the dataset from the current group to the specified target group.
        """,
        scripts.String(
            P_DATA_TYPE, optional=False,grouping="1",
            description="Choose source of images (only Images supported)",
            values=data_types, default="Image"),

        scripts.List(
            P_IDS,  optional=False, grouping="2",
            description="List of Images IDs to link to another"
                        " group.").ofType(rlong(0)),
        scripts.String(
            P_GROUP, optional=False, grouping="3",
            description="Target group where to copy images"),

        scripts.String(
            P_DATASET, optional=True, grouping="4",
            description="New dataset to create in the given group. Leave blank to copy images in orphaned folder.",
            default=""),

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
        message, res_obj, err = duplicate_and_move_to_group(conn, script_params)
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
