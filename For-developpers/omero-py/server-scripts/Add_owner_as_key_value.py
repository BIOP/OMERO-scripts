"""
 Add_owner_as_key_value.py
 Adds data owner as a key-value pair to images
-----------------------------------------------------------------------------
  Copyright (C) 2025
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
Created by Rémy Dornier, based on Christian Evenhuis work.
"""

import omero
from omero.gateway import BlitzGateway
from omero.rtypes import rstring
import omero.scripts as scripts
from datetime import date
import copy
from collections import OrderedDict


P_DATA_TYPE = "Data_Type"
P_IDS = "IDs"


def get_existing_map_annotations(obj, namespace):
    """Get all Map Annotations linked to the object"""

    ord_dict = OrderedDict()
    for ann in obj.listAnnotations(ns=namespace):
        if isinstance(ann, omero.gateway.MapAnnotationWrapper):
            kvs = ann.getValue()
            for k, v in kvs:
                if k not in ord_dict:
                    ord_dict[k] = set()
                ord_dict[k].add(v)
    return ord_dict


def annotate_object(conn, obj, key, val):
    """
    Add a key value pair to the object
    Code taken and adapted from Christian Evenhuis,
    https://github.com/ome/omero-scripts/blob/develop/omero/annotation_scripts/KeyVal_from_csv.py
    """

    obj_updated = False
    namespace = "data.ownership"
    existing_kv = get_existing_map_annotations(obj, namespace)
    updated_kv = copy.deepcopy(existing_kv)

    print("Existing kv:")
    for k, vset in existing_kv.items():
        for v in vset:
            print("   ", k, v)

    print("Adding kv:")

    if key not in updated_kv:
        updated_kv[key] = set()
    print("   ", key, val)
    updated_kv[key].add(val)

    if existing_kv != updated_kv:
        try:
            # convert the ordered dict to a list of lists
            kv_list = []

            for k, vset in updated_kv.items():
                for v in vset:
                    kv_list.append([k, v])

            print("The key-values pairs are different")
            anns = list(obj.listAnnotations(ns=namespace))
            map_anns = [ann for ann in anns if isinstance(ann, omero.gateway.MapAnnotationWrapper)]
            print(map_anns)

            if len(map_anns) > 0:
                map_ann = map_anns[0]
                map_ann.setValue(kv_list)
                map_ann.save()
            else:
                map_ann = omero.gateway.MapAnnotationWrapper(conn)
                map_ann.setNs(namespace)
                map_ann.setValue(kv_list)
                map_ann.save()
                obj.linkAnnotation(map_ann)

            print("Map Annotation created", map_ann.id)
            obj_updated = True

        except omero.SecurityViolation:
            print("You do not have the right to write annotations for ", obj.OMERO_CLASS, "", obj.getId())
    else:
        print("No change change in kv")

    return obj_updated


def process_image(conn, image, key, owner):
    """
    Add the owner as key value pair to an image
    return 1 if owner has been added, 0 otherwise
    """

    return 1 if annotate_object(conn, image, key, owner) is True else 0


def process_dataset(conn, dataset, key, owner):
    """
    Add the owner as key value pair to a dataset and its children
    return the number of processed images
    """

    n_image = 0
    for image in dataset.listChildren():
        n_image += process_image(conn, image, key, owner)

    return n_image


def process_project(conn, project, key, owner):
    """
    Add the owner as key value pair to a project and its children
    return the number of processed images & datasets
    """

    n_image = 0
    for dataset in project.listChildren():
        n_image += process_dataset(conn, dataset, key, owner)

    return n_image


def process_well(conn, well, key, owner):
    """
    Add the owner as key value pair to a well and its children
    return the number of processed images
    """

    n_image = 0
    for wellSample in well.listChildren():
        n_image += process_image(conn, wellSample.getImage(), key, owner)

    return n_image


def process_plate(conn, plate, key, owner):
    """
    Add the owner as key value pair to a plate and its children
    return the number of processed images & wells
    """

    n_image = 0
    for well in plate.listChildren():
        n_image += process_well(conn, well, key, owner)

    return n_image


def process_screen(conn, screen, key, owner):
    """
    Add the owner as key value pair to a screen and its children
    return the number of processed images, wells & plates
    """

    n_image = 0
    for plate in screen.listChildren():
        n_image += process_plate(conn, plate, key, owner)

    return n_image


def add_owner_as_keyval(conn, script_params):
    """
    Get the given container(s) or given experimenter(s) and scan all their children to add
    data owner as a key-value pair to all of them.
    """

    # select the object type (image, dataset, project, well, plate, screen, user)
    object_type = script_params[P_DATA_TYPE]

    # enter its corresponding ID (except for 'user' : enter the username)
    object_id_list = script_params[P_IDS]

    today = date.today().strftime("%Y%m%d")
    key = "owner_" + today

    n_image = 0
    owner = ""

    for object_id in object_id_list:
        """ add owner to all objects owned by the specified user"""
        if object_type == 'User':
            # set the group to all
            conn.SERVICE_OPTS.setOmeroGroup('-1')

            # get the user
            user = conn.getObject("experimenter", attributes={"omeName": object_id})

            if user is not None:
                # get the list of groups to search in
                group_list = []
                if conn.getUser().isAdmin():
                    for gem in user.copyGroupExperimenterMap():
                        group_list.append(gem.getParent().getId().getValue())

                    # get sudo connection
                    user_name = user.getName()
                    user_conn = conn.suConn(user_name, ttl=600000)
                    user_id = user_conn.getUser().getId()
                else:
                    for g in conn.getGroupsMemberOf():
                        group_list.append(g.getId())
                    user_conn = conn
                    user_id = user.getId()

                # get the owner
                owner = ""
                owner += user.getFirstName() + " "
                owner += user.getLastName()

                for g_id in group_list:
                    # set the group
                    user_conn.SERVICE_OPTS.setOmeroGroup(g_id)

                    # list all user's projects
                    projects = user_conn.getObjects("Project", opts={'owner': user_id})
                    for project in projects:
                        n_image += process_project(user_conn, project, key, owner)

                    # lists all user's screens
                    screens = user_conn.getObjects("screen", opts={'owner': user_id})
                    for screen in screens:
                        n_image += process_screen(user_conn, screen, key, owner)

                    # lists all user's orphaned dataset
                    orphaned_datasets = user_conn.getObjects("dataset", opts={'owner': user_id, 'orphaned': True})
                    for orphaned_dataset in orphaned_datasets:
                        n_image += process_dataset(user_conn, orphaned_dataset, key, owner)

                    # lists all user's orphaned plates
                    orphaned_plates = user_conn.getObjects("plate", opts={'owner': user_id, 'orphaned': True})
                    for orphaned_plate in orphaned_plates:
                        n_image += process_plate(user_conn, orphaned_plate, key, owner)

                    # lists all user's orphaned images
                    orphaned_images = user_conn.getObjects("image", opts={'owner': user_id, 'orphaned': True})
                    for orphaned_image in orphaned_images:
                        n_image += process_image(user_conn, orphaned_image, key, owner)

                # close the user connection
                if conn.getUser().isAdmin():
                    user_conn.close()

            else:
                print("The user", object_id, "does not exists or you do not have access to his/her data")

        else:
            """ add owner to the specified object and its children"""
            # convert to long
            object_id = int(object_id)

            # search in all the user's group
            conn.SERVICE_OPTS.setOmeroGroup('-1')

            # get the object
            omero_object = conn.getObject(object_type, object_id)

            if omero_object is not None:
                if conn.getUser().isAdmin():
                    # get sudo connection
                    user_name = omero_object.getOwner().getOmeName()
                    user_conn = conn.suConn(user_name, ttl=600000)
                    user_conn.SERVICE_OPTS.setOmeroGroup('-1')
                    omero_object = user_conn.getObject(object_type, object_id)
                else:
                    user_conn = conn

            if omero_object is not None:
                # set the correct group Id
                user_conn.SERVICE_OPTS.setOmeroGroup(omero_object.getDetails().getGroup().getId())

                # get the owner
                owner = ""
                owner += omero_object.getOwner().getFirstName() + " "
                owner += omero_object.getOwner().getLastName()

                # select object type and add owner as key-value pair
                if object_type == 'Image':
                    n_image += process_image(user_conn, omero_object, key, owner)
                if object_type == 'Dataset':
                    n_image += process_dataset(user_conn, omero_object, key, owner)
                if object_type == 'Project':
                    n_image += process_project(user_conn, omero_object, key, owner)
                if object_type == 'Well':
                    n_image += process_well(user_conn, omero_object, key, owner)
                if object_type == 'Plate':
                    n_image += process_plate(user_conn, omero_object, key, owner)
                if object_type == 'Screen':
                    n_image += process_screen(user_conn, omero_object, key, owner)

                # close the user connection
                if conn.getUser().isAdmin():
                    user_conn.close()

            else:
                print(object_type, object_id, "does not exist or you do not have access to it")

    # build summary message
    if owner == "":
        message = "Owner cannot be added"
    else:
        message = f"Added '{owner}' as owner to {n_image} image(s)"
    print(message)

    return message


def run_script():
    data_types = [rstring('Image'), rstring('Dataset'), rstring('Project'), rstring('Well'), rstring('Plate'),
                  rstring('Screen'), rstring('User')]
    client = scripts.client(
        'Add_Owner_as_KeyVal',
        """
    This script adds the owner name as a key-value pair to all images in the selected container(s).
        """,
        scripts.String(
            P_DATA_TYPE, optional=False, grouping="1",
            description="Choose source of images",
            values=data_types, default="Dataset"),

        scripts.List(
            P_IDS, optional=False, grouping="2",
            description="Object ID(s) or username(s).").ofType(rstring('')),

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
        message = add_owner_as_keyval(conn, script_params)
        client.setOutput("Message", rstring(message))

    finally:
        client.closeSession()


if __name__ == "__main__":
    run_script()
