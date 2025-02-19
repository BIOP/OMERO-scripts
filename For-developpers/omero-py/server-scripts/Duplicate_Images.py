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
from io import StringIO
import sys

P_DATA_TYPE = "Data_Type"
P_IDS = "IDs"
P_DUP_NUM = "Number of duplicate to create"

OMERO_SERVER = "omero-server.epfl.ch"
OMERO_WEBSERVER = "omero.epfl.ch"
PORT = "4064"


class Capturing(list):
    """
    Class to read the stdout of the command line
    Here, to read the --report output by the omero.cmd.duplicate
    """
    def __enter__(self):
        self._stdout = sys.stdout
        sys.stdout = self._stringio = StringIO()
        return self

    def __exit__(self, *args):
        self.extend(self._stringio.getvalue().splitlines())
        del self._stringio    # free up some memory
        sys.stdout = self._stdout


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
    raw_input_ids = script_params[P_IDS]

    # Type of object to duplicate
    data_type = script_params[P_DATA_TYPE]
    parent_objs = [None]

    # Number of image copy
    n_iter = script_params[P_DUP_NUM]

    # Build the duplicate command
    str_ids = [str(img_id) for img_id in raw_input_ids]
    import_args = ["duplicate",
                   f"{data_type}:{','.join(str_ids)}", "--report"
                   ]

    for copy_id in range(n_iter):
        # open cli connection
        with omero.cli.cli_login("-k", "%s" % conn.c.getSessionId(), "-s", OMERO_SERVER, "-p", PORT) as cli:
            cli.register('duplicate', DuplicateControl, '_')
            cli.register('sessions', SessionsControl, '_')
            # launch duplication
            try:
                with Capturing() as output:
                    cli.invoke(import_args, strict=True)
                print("SUCCESS", f"Duplicated images {str_ids}")
            except PermissionError as err:
                message = f"Error during duplication of images {str_ids}: {err}"
                cli.get_client().closeSession()
                cli.close()
                return message, None, err
    
            cli.get_client().closeSession()

        # extract ids of duplicated images
        print(output)
        duplicated_images_ids = extract_duplicate_image_ids(output, "Image")
        print(f"Duplicated images : {duplicated_images_ids}")

        # create a target dataset for orphaned images or get the duplicated parent containers
        dataset_id = -1
        if data_type == "Image":
            dataset_name = f"Duplicated_images_{copy_id + 1}"
            dataset_id = create_dataset(conn, dataset_name)
            parent_objs = [conn.getObject("Dataset", dataset_id)]
        else:
            duplicated_parent_ids = extract_duplicate_image_ids(output, data_type)
            if len(duplicated_parent_ids) > 0:
                parent_objs = [p_obj for p_obj in conn.getObjects(data_type, duplicated_parent_ids)]
            else:
                parent_objs = []

        if len(parent_objs) > 0 and parent_objs[0] is not None:
            # create and add the KVP on parent container
            parent_kvps = [
                [f"Source {data_type.lower()}",
                 f"https://{OMERO_WEBSERVER}/webclient/?show={'|'.join([f'{data_type.lower()}-{im_id}' for im_id in str_ids])}"]
            ]

            for parent_obj in parent_objs:
                add_annotation_key_value(conn, parent_obj, parent_kvps)

            # get the duplicated images
            if len(duplicated_images_ids) > 0:
                duplicated_images = conn.getObjects("Image", duplicated_images_ids)
                img_kvps = [
                    ["Duplicated by", conn.getUser().getFullName()],
                    ["Duplication date", datetime.now().astimezone().strftime("%Y-%m-%d %H:%M:%S")]]

                # add kvps to duplicated & move orphaned images to the right dataset
                for dup_img in duplicated_images:
                    add_annotation_key_value(conn, dup_img, img_kvps)
                    if data_type == "Image" and dataset_id > 0:
                        link = omero.model.DatasetImageLinkI()
                        link.setChild(omero.model.ImageI(dup_img.id, False))
                        link.setParent(omero.model.DatasetI(dataset_id, False))
                        conn.getUpdateService().saveObject(link)

    return f"Successful duplication of {data_type} : {str_ids} ", parent_objs[0], None


def extract_duplicate_image_ids(report_list, d_type):
    """
    Parse the report generated by omero.cmd.duplicate and extract OMERO IDs
    of the specified type

    Parameters
    ----------
    report_list: List of str
        report generated by omero.cmd.duplicate
    d_type: str
        container type

    Returns
    -------


    """
    to_search = f"{d_type}:"
    for line in report_list:
        if line.strip().startswith(to_search):
            ids = line.strip().replace(to_search, "")
            omero_ids = []
            for group_ids in ids.split(","):
                start_end_ids = group_ids.split("-")
                for i in range(int(start_end_ids[0]), int(start_end_ids[-1]) + 1):
                    omero_ids.append(i)
            return omero_ids
    return []


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
            description="Choose images or containers to duplicate",
            values=data_types, default="Image"),

        scripts.List(
            P_IDS,  optional=False, grouping="2",
            description="List of IDs to duplicate").ofType(rlong(0)),
        scripts.Int(
            P_DUP_NUM, optional=False, grouping="3",
            description="How many copies of the selected images/containers do you want to create ?", default=1, min=1),

        authors=["Rémy Dornier"],
        institutions=["EPFL - BIOP"],
        contact="omero@groupes.epfl.ch",
        version="2.0.0"
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
