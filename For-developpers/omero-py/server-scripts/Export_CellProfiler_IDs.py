# coding=utf-8
"""
 Export_to_csv.py

 Reads the metadata associated with the images in a dataset
 and creates a csv file attached to dataset

-----------------------------------------------------------------------------
  Copyright (C) 2018 - 2024
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
Created by Christian Evenhuis

"""

import omero
from omero.gateway import BlitzGateway
from omero.rtypes import rstring, rlong, robject
import omero.scripts as scripts

import tempfile
import os
import csv

CHILD_OBJECTS = {
    "Project": "Dataset",
    "Dataset": "Image",
    "Screen": "Plate",
    "Plate": "Well",
    "Well": "WellSample",
    "WellSample": "Image"
}

ALLOWED_PARAM = {
    "Project": ["Project", "Dataset", "Image"],
    "Dataset": ["Dataset", "Image"],
    "Image": ["Image"],
    "Screen": ["Screen", "Plate", "Well", "Acquisition", "Image"],
    "Plate": ["Plate", "Well", "Acquisition", "Image"],
    "Well": ["Well", "Image"],
    "Acquisition": ["Acquisition", "Image"],
    "Tag": ["Project", "Dataset", "Image",
            "Screen", "Plate", "Well", "Acquisition"]
}

P_DTYPE = "Data_Type"  # Do not change
P_IDS = "IDs"  # Do not change
P_TARG_DTYPE = "Target Data_Type"

# Add your OMERO.web URL for direct download from link:
# eg https://omero-adress.org/webclient
WEBCLIENT_URL = ""


def get_obj_name(omero_obj):
    """ Helper function """
    if omero_obj.OMERO_CLASS == "Well":
        return omero_obj.getWellPos().upper()
    else:
        return omero_obj.getName()


def get_children_recursive(source_object, target_type):
    """
    Recursively retrieve child objects of a specified type from a source
    OMERO object.

    :param source_object: The OMERO source object from which child objects
    are retrieved.
    :type source_object: omero.model.<ObjectType>
    :param target_type: The OMERO object type to be retrieved as children.
    :type target_type: str
    :return: A list of child objects of the specified target type.
    :rtype: list
    """
    if CHILD_OBJECTS[source_object.OMERO_CLASS] == target_type:
        # Stop condition, we return the source_obj children
        if source_object.OMERO_CLASS != "WellSample":
            return source_object.listChildren()
        else:
            return [source_object.getImage()]
    else:  # Not yet the target
        result = []
        for child_obj in source_object.listChildren():
            # Going down in the Hierarchy list
            result.extend(get_children_recursive(child_obj, target_type))
        return result


def target_iterator(conn, source_object, target_type, is_tag):
    """
    Iterate over and yield target objects of a specified type from a source
    OMERO object.

    :param conn: OMERO connection for server interaction.
    :type conn: omero.gateway.BlitzGateway
    :param source_object: Source OMERO object to iterate over.
    :type source_object: omero.model.<ObjectType>
    :param target_type: Target object type to retrieve.
    :type target_type: str
    :param is_tag: Flag indicating if the source object is a tag.
    :type is_tag: bool
    :yield: Target objects of the specified type.
    :rtype: omero.model.<ObjectType>
    """
    if target_type == source_object.OMERO_CLASS:
        target_obj_l = [source_object]
    elif source_object.OMERO_CLASS == "PlateAcquisition":
        # Check if there is more than one Run, otherwise
        # it's equivalent to start from a plate (and faster this way)
        plate_o = source_object.getParent()
        wellsamp_l = get_children_recursive(plate_o, "WellSample")
        if len(list(plate_o.listPlateAcquisitions())) > 1:
            # Only case where we need to filter on PlateAcquisition
            run_id = source_object.getId()
            wellsamp_l = filter(lambda x: x._obj.plateAcquisition._id._val
                                == run_id, wellsamp_l)
        target_obj_l = [wellsamp.getImage() for wellsamp in wellsamp_l]
    elif target_type == "PlateAcquisition":
        # No direct children access from a plate
        if source_object.OMERO_CLASS == "Screen":
            plate_l = get_children_recursive(source_object, "Plate")
        elif source_object.OMERO_CLASS == "Plate":
            plate_l = [source_object]
        target_obj_l = [r for p in plate_l for r in p.listPlateAcquisitions()]
    elif is_tag:
        target_obj_l = conn.getObjectsByAnnotations(target_type,
                                                    [source_object.getId()])
        # Need that to load objects
        obj_ids = [o.getId() for o in target_obj_l]
        if len(obj_ids) > 0:
            target_obj_l = list(conn.getObjects(target_type, obj_ids))
        else:
            target_obj_l = []
    else:
        target_obj_l = get_children_recursive(source_object,
                                              target_type)

    print(f"Iterating objects from {source_object}:")
    for target_obj in target_obj_l:
        print(f"\t- {target_obj}")
        yield target_obj


def main_loop(conn, script_params):
    """
    Main loop to process each object, ancestry,
    and writing to a single .txt file.

    :param conn: OMERO connection for server interaction.
    :type conn: omero.gateway.BlitzGateway
    :param script_params: Dictionary of script parameters including data types,
        IDs, namespaces, and flags for options like including ancestry and
        tags.
    :type script_params: dict
    :return: Message regarding CSV attachment status, file annotation, and
        result object.
    :rtype: tuple
    """
    source_type = script_params[P_DTYPE]
    target_type = script_params[P_TARG_DTYPE]
    source_ids = script_params[P_IDS]

    # One file output per given ID
    obj_ancestry_l = []

    obj_id_l = []
    for source_object in conn.getObjects(source_type, source_ids):

        result_obj = source_object
        if source_type == "TagAnnotation":
            result_obj = None  # Attach result txt on the first object
        is_tag = source_type == "TagAnnotation"

        for target_obj in target_iterator(conn, source_object,
                                          target_type, is_tag):

            obj_id_l.append(target_obj.getId())

            if result_obj is None:
                result_obj = target_obj
        print("\n------------------------------------\n")

    csv_name = f"{get_obj_name(source_object)}_{target_type}-CellProfiler.txt"

    # Complete ancestry for image/dataset/plate without parents
    norm_ancestry_l = []
    if len(obj_ancestry_l) > 0:
        # Issue with image that don't have a plateacquisition
        # if combined with images that have
        max_level = max(map(lambda x: len(x), obj_ancestry_l))
        for ancestry in obj_ancestry_l:
            norm_ancestry_l.append([("", "")] *
                                   (max_level - len(ancestry))
                                   + ancestry)

    rows = build_rows(obj_id_l)
    separator = "\n"
    file_ann = attach_csv(conn, result_obj, rows, separator, csv_name)

    if file_ann is None:
        message = "The TXT is printed in output, no file could be attached:"
    else:
        message = ("The txt is attached to " +
                   f"{result_obj.OMERO_CLASS}:{result_obj.getId()}.")

    return message, file_ann, result_obj


def build_rows(obj_id_l):
    """
    Sorts and concatenates rows, including object IDs, names, and ancestry
    if applicable.
    :param obj_id_l: List of object IDs.
    :type obj_id_l: list

    """
    rows = []
    for obj_id in obj_id_l:
        cp_id = ["omero:iid="+str(obj_id)]
        rows.append(cp_id)

    return rows


def attach_csv(conn, obj_, rows, separator, csv_name):
    """
    Attaches a generated CSV file to an OMERO object.

    :param conn: OMERO connection for server interaction.
    :type conn: omero.gateway.BlitzGateway
    :param obj_: OMERO object to which the CSV file will be attached.
    :type obj_: omero.model.<ObjectType>
    :param rows: Data rows to write into the CSV.
    :type rows: list
    :param separator: Separator character for CSV file.
    :type separator: str
    :param csv_name: Name for the generated CSV file.
    :type csv_name: str
    :return: File annotation object if the file is attached, None otherwise.
    :rtype: omero.model.FileAnnotation
    """
    if not obj_.canAnnotate() and WEBCLIENT_URL == "":
        for row in rows:
            print(f"{separator.join(row)}")
        return None

    # create the tmp directory
    tmp_dir = tempfile.mkdtemp(prefix='MIF_meta')
    (fd, tmp_file) = tempfile.mkstemp(dir=tmp_dir, text=True)
    with os.fdopen(fd, 'w', encoding="utf-8") as tfile:
        csvwriter = csv.writer(tfile,
                               delimiter=separator,
                               quotechar='"',
                               quoting=csv.QUOTE_MINIMAL,
                               lineterminator="\n")
        for row in rows:
            csvwriter.writerow(row)

    # link it to the object
    file_ann = conn.createFileAnnfromLocalFile(
        tmp_file, origFilePathAndName=csv_name,
        ns='CellProfiler_export')

    if obj_.canAnnotate():
        obj_.linkAnnotation(file_ann)
        print(f"{file_ann} linked to {obj_}")

    # remove the tmp file
    os.remove(tmp_file)
    os.rmdir(tmp_dir)

    return file_ann.getFile()


def run_script():
    """
    Entry point for the script, called by the client.

    Sets up and executes the main loop based on user-defined parameters,
    and generates output message or URL for the CSV file download.

    :return: Sets output messages and result objects for OMERO client session.
    :rtype: None
    """

    # Cannot add fancy layout if we want auto fill and selct of object ID
    source_types = [
                    rstring("Project"), rstring("Dataset"), rstring("Image"),
                    rstring("Screen"), rstring("Plate"), rstring("Well"),
                    rstring("Acquisition"), rstring("Image"), rstring("Tag"),
    ]

    # Duplicate Image for UI, but not a problem for script
    target_types = [
                    rstring("<selected>"), rstring("Project"),
                    rstring("- Dataset"), rstring("-- Image"),
                    rstring("Screen"), rstring("- Plate"),
                    rstring("-- Well"), rstring("-- Acquisition"),
                    rstring("--- Image"), rstring("<all (from selected)>")
    ]

    # Here we define the script name and description.
    # Good practice to put url here to give users more guidance on how to run
    # your script.
    client = scripts.client(
        'Export CellProfiler IDs',
        """
    Exports in a txt file, with OMERO IDs, formatting in a way that CellProfiler 
    is able to read the images from OMERO and apply a pipeline on them.
    \t
        """,  # Tabs are needed to add line breaks in the HTML

        scripts.String(
            P_DTYPE, optional=False, grouping="1",
            description="Data type of the parent objects.",
            values=source_types, default="Dataset"),

        scripts.List(
            P_IDS, optional=False, grouping="1.1",
            description="IDs of the parent objects").ofType(rlong(0)),

        scripts.String(
            P_TARG_DTYPE, optional=False, grouping="1.2",
            description="Data type to process from the selected " +
            "parent objects.",
            values=target_types, default="<selected>"),

        authors=["Christian Evenhuis", "MIF", "Tom Boissonnet", "Rémy Dornier"],
        institutions=["University of Technology Sydney", "CAi HHU", "EPFL"],
        contact="https://forum.image.sc/tag/omero",
        version="1.0.0",
    )
    try:
        params = parameters_parsing(client)

        # wrap client to use the Blitz Gateway
        conn = BlitzGateway(client_obj=client)
        messages = []
        targets = params[P_TARG_DTYPE]
        for target in targets:  # Loop on target, use case of process all
            params[P_TARG_DTYPE] = target
            message, fileann, res_obj = main_loop(conn, params)
            messages.append(message)
        client.setOutput("Message", rstring(" ".join(messages)))

        if res_obj is not None and fileann is not None:
            href = f"{WEBCLIENT_URL}/download_original_file/{fileann.getId()}"
            if WEBCLIENT_URL != "":
                url = omero.rtypes.wrap({
                    "type": "URL",
                    "href": href,
                    "title": "TXT file for CellProfiler",
                })
                client.setOutput("URL", url)
            else:
                client.setOutput("Result", robject(res_obj._obj))

    except AssertionError as err:
        # Display assertion errors in OMERO.web activities
        client.setOutput("ERROR", rstring(err))
        raise AssertionError(str(err))
    finally:
        client.closeSession()


def parameters_parsing(client):
    """
    Parses and validates input parameters from the client.

    :param client: Script client used to obtain input parameters.
    :type client: omero.scripts.ScriptClient
    :return: Parsed parameters dictionary with defaults for unspecified
        options.
    :rtype: dict
    """
    params = {}

    for key in client.getInputKeys():
        if client.getInput(key):
            # unwrap rtypes to String, Integer etc
            params[key] = client.getInput(key, unwrap=True)

    if params[P_TARG_DTYPE] == "<selected>":
        params[P_TARG_DTYPE] = params[P_DTYPE]
    elif params[P_TARG_DTYPE].startswith("-"):
        # Getting rid of the trailing '---' added for the UI
        params[P_TARG_DTYPE] = params[P_TARG_DTYPE].split(" ")[1]

    if params[P_TARG_DTYPE] != "<all (from selected)>":
        assert params[P_TARG_DTYPE] in ALLOWED_PARAM[params[P_DTYPE]], \
               (f"{params['Target Data_Type']} is not a valid target for " +
                f"{params['Data_Type']}.")

    if params[P_TARG_DTYPE] == "<all (from selected)>":
        params[P_TARG_DTYPE] = ALLOWED_PARAM[params[P_DTYPE]]
    else:
        # Convert to list for iteration over single element
        params[P_TARG_DTYPE] = [params[P_TARG_DTYPE]]
    params[P_TARG_DTYPE] = ["PlateAcquisition" if el == "Acquisition" else el
                            for el in params[P_TARG_DTYPE]]

    if params[P_DTYPE] == "Tag":
        params[P_DTYPE] = "TagAnnotation"

    print("Input parameters:")
    keys = [P_DTYPE, P_IDS, P_TARG_DTYPE]
    for k in keys:
        print(f"\t- {k}: {params[k]}")
    print("\n####################################\n")

    return params


if __name__ == "__main__":
    run_script()
