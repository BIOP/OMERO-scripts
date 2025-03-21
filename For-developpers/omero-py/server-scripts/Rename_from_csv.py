# coding=utf-8
"""
 Rename_from_csv.py

 Rename a list of target objects on OMERO from a CSV file.

-----------------------------------------------------------------------------
  Copyright (C) 2018 - 2025
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
Created by Rémy Dornier, based on Christian Evenhuis, Tom Boissonnet & Will Moore work
in Import_from_csv.py script

"""

import omero
from omero.gateway import BlitzGateway
from omero.rtypes import rstring, rlong, robject
import omero.scripts as scripts
from omero.constants.metadata import NSCLIENTMAPANNOTATION, NSINSIGHTTAGSET
from omero.model import AnnotationAnnotationLinkI
from omero.util.populate_roi import DownloadingOriginalFileProvider
import csv
import re


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
}

P_DTYPE = "Data_Type"  # Do not change
P_FILE_ANN = "File_Annotation"  # Do not change
P_IDS = "IDs"  # Do not change
P_TARG_DTYPE = "Target Data_Type"
P_EXCL_COL = "Columns to exclude"
P_TARG_COLID = "Target ID colname"
P_TARG_COLNAME = "Target name colname"
P_EXCL_EMPTY = "Exclude empty values"
P_CSVSEP = "CSV separator"



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
        if target_obj.canAnnotate():
            print(f"\t- {target_obj}")
            yield target_obj
        else:
            print(f"\t- Annotate {target_obj} is not permitted, skipping")
    print()


def main_loop(conn, script_params):
    """
    Main function to annotate objects in OMERO based on CSV input.

    This function reads a CSV file, identifies objects in OMERO based on
    specified criteria, and annotates them with metadata from the CSV.

    Startup:
     - Find CSV and reads it
    For every object:
     - Gather name and ID
    Finalize:
     - Find a match between CSV rows and objects
     - Annotate the objects
     - (opt) attach the CSV to the source object

    :param conn: OMERO connection for interacting with the OMERO server.
    :type conn: omero.gateway.BlitzGateway
    :param script_params: Dictionary of parameters passed to the script,
        specifying the source and target data types, IDs, annotations, CSV
        separator, namespaces, and other options.
    :type script_params: dict
    :return: Message with annotation summary and the first annotated target
        object.
    :rtype: tuple
    """
    source_type = script_params[P_DTYPE]
    target_type = script_params[P_TARG_DTYPE]
    source_ids = script_params[P_IDS]
    file_ids = script_params[P_FILE_ANN]
    to_exclude = script_params[P_EXCL_COL]
    target_id_colname = script_params[P_TARG_COLID]
    target_name_colname = script_params[P_TARG_COLNAME]
    separator = script_params[P_CSVSEP]
    exclude_empty_value = script_params[P_EXCL_EMPTY]
    file_ann_multiplied = script_params["File_Annotation_multiplied"]

    ntarget_processed = 0
    ntarget_updated = 0
    missing_names = set()
    processed_names = set()
    total_missing_names = 0

    result_obj = None

    # One file output per given ID
    source_objects = conn.getObjects(source_type, source_ids)
    for source_object, file_ann_id in zip(source_objects, file_ids):
        ntarget_updated_curr = 0

        # Find the file from the user input
        if file_ann_id is not None:
            file_ann = conn.getObject("Annotation", oid=file_ann_id)
            assert file_ann is not None, f"Annotation {file_ann_id} not found"
            assert file_ann.OMERO_TYPE == omero.model.FileAnnotationI, \
                ("The provided annotation ID must reference a " +
                 f"FileAnnotation, not a {file_ann.OMERO_TYPE}")
        else:
            file_ann = get_original_file(source_object)

        # Get the list of things to annotate
        is_tag = source_type == "TagAnnotation"
        target_obj_l = list(target_iterator(conn, source_object,
                                            target_type, is_tag))

        # Find the most suitable object to link the file to
        if is_tag and len(target_obj_l) > 0:
            obj_to_link = target_obj_l[0]
        else:
            obj_to_link = source_object
        link_file_ann(conn, obj_to_link, file_ann)

        original_file = file_ann.getFile()._obj
        rows, header = read_csv(conn, original_file, separator)

        # Index of the column used to identify the targets. Try for IDs first
        idx_id, idx_name = -1, -1
        if target_id_colname in header:
            idx_id = header.index(target_id_colname)
        if target_name_colname in header:
            idx_name = header.index(target_name_colname)
        cols_to_ignore = [header.index(el) for el in to_exclude
                          if el in header]

        assert (idx_id != -1) or (idx_name != -1), \
            ("Neither the column for the objects' name or" +
             " the objects' index were found")

        use_id = idx_id != -1  # use the obj_idx column if exist
        if not use_id:
            idx_id = idx_name
            # check if the names in the .csv contain duplicates
            name_list = [row[idx_id] for row in rows]
            duplicates = {name for name in name_list
                          if name_list.count(name) > 1}
            print("duplicates:", duplicates)
            assert not len(duplicates) > 0, \
                (f"The .csv contains duplicates {duplicates} which makes" +
                 " it impossible to correctly allocate the annotations.")

            # Identify target-objects by name fail if two have identical names
            target_d = dict()
            for target_obj in target_obj_l:
                name = get_obj_name(target_obj)
                assert name not in target_d.keys(), \
                    ("Target objects identified by name have at " +
                     f"least one duplicate: {name}")
                target_d[name] = target_obj
        else:
            # Setting the dictionnary target_id:target_obj
            # keys as string to match CSV reader output
            target_d = {str(target_obj.getId()): target_obj
                        for target_obj in target_obj_l}
        ntarget_processed += len(target_d)

        ok_idxs = [i for i in range(len(header)) if i not in cols_to_ignore]
        for row in rows:
            # Iterate the CSV rows and search for the matching target
            target_id = row[idx_id]
            # skip empty rows
            if target_id == "":
                continue

            if target_id in target_d.keys():
                target_obj = target_d[target_id]
                # add name/id to processed set
                if file_ann_multiplied:
                    processed_names.add(target_id)
            else:
                # add name/id to missing set
                if file_ann_multiplied:
                    missing_names.add(target_id)
                else:
                    total_missing_names += 1
                    print(f"Not found: {target_id}")
                continue

            parsed_row = [row[i] for i in ok_idxs]
            updated = annotate_object(target_obj, parsed_row, exclude_empty_value)

            if updated:
                if result_obj is None:
                    result_obj = target_obj
                ntarget_updated += 1
                ntarget_updated_curr += 1

        print("\n------------------------------------\n")

    message = (
        "Added Annotations to " +
        f"{ntarget_updated}/{ntarget_processed} {target_type}(s)."
    )

    if file_ann_multiplied and len(missing_names) > 0:
        # subtract the processed names/ids from the
        # missing ones and print the missing names/ids
        missing_names = missing_names - processed_names
        if len(missing_names) > 0:
            print(f"Not found: {missing_names}")
        total_missing_names = len(missing_names)

    if total_missing_names > 0:
        message += (
            f". {total_missing_names} {target_type}(s) not found "
            f"(using {'ID' if use_id else 'name'} to identify them)."
        )

    return message, result_obj


def get_original_file(omero_obj):
    """
    Retrieve the latest CSV or TSV file annotation linked to an OMERO object.

    :param omero_obj: OMERO object to retrieve file annotation from.
    :type omero_obj: omero.model.<ObjectType>
    :return: The most recent CSV or TSV file annotation.
    :rtype: omero.model.FileAnnotation
    """
    file_ann = None
    for ann in omero_obj.listAnnotations():
        if ann.OMERO_TYPE == omero.model.FileAnnotationI:
            file_name = ann.getFile().getName()
            # Pick file by Ann ID (or name if ID is None)
            if file_name.endswith(".csv") or file_name.endswith(".tsv"):
                if (file_ann is None) or (ann.getDate() > file_ann.getDate()):
                    # Get the most recent file
                    file_ann = ann

    assert file_ann is not None, \
        (f"No .csv FileAnnotation was found on {omero_obj.OMERO_CLASS}" +
         f":{get_obj_name(omero_obj)}:{omero_obj.getId()}")

    return file_ann


def read_csv(conn, original_file, delimiter):
    """
    Read a CSV file linked to an OMERO FileAnnotation and process its contents.

    :param conn: OMERO connection for accessing the server.
    :type conn: omero.gateway.BlitzGateway
    :param original_file: File object containing the CSV data.
    :type original_file: omero.model.OriginalFileI
    :param delimiter: Delimiter for the CSV file; detected if None.
    :type delimiter: str
    :return: Parsed rows, header, and namespaces from the CSV file + map of keys grouped according
     to their respective values unicity
    :rtype: tuple
    """
    print("Using FileAnnotation",
          f"{original_file.id.val}:{original_file.name.val}")
    provider = DownloadingOriginalFileProvider(conn)
    # read the csv
    # Needs omero-py 5.9.1 or later

    try:
        temp_file = provider.get_original_file_data(original_file)
        with open(temp_file.name, mode="rt", encoding='utf-8-sig') as f:
            csv_content = f.readlines()
    except UnicodeDecodeError as e:
        assert False, ("Error while reading the csv, convert your " +
                       "file to utf-8 encoding" +
                       str(e))

    # Read delimiter from CSV first line if exist
    re_delimiter = re.compile("[\"']?sep=(?P<delimiter>.?)[\"']?")
    match = re_delimiter.match(csv_content[0])
    if match:  # Need to discard first row
        csv_content = csv_content[1:]
        if delimiter is None:  # (and we detect delimiter if not given)
            delimiter = match.group('delimiter')

    if delimiter is None:
        try:
            # Sniffing on a maximum of four lines
            delimiter = csv.Sniffer().sniff("\n".join(csv_content[:4]),
                                            "|,;\t").delimiter
        except Exception as e:
            assert False, ("Failed to sniff CSV delimiter: " + str(e))
    rows = list(csv.reader(csv_content, delimiter=delimiter))

    rowlen = len(rows[0])
    error_msg = (
        "CSV rows length mismatch: Header has {} " +
        "items, while line {} has {}"
    )
    for i in range(1, len(rows)):
        assert len(rows[i]) == rowlen, error_msg.format(
            rowlen, i, len(rows[i])
        )

    # keys are in the header row (first row for no namespaces
    # second row with namespaces declared)
    header = [el.strip() for el in rows[0]]
    rows = rows[1:]

    print(f"Header: {header}\n")
    return rows, header


def annotate_object(obj, new_name, exclude_empty_value):
    """
    Annotate a target object with key-value pairs and tags based on a row
    of CSV data.

    :param obj: OMERO object to be annotated.
    :type obj: omero.model.<ObjectType>
    :param new_name: replacement text
    :type new_name: list of str
    :param exclude_empty_value: If True, excludes empty values in annotations.
    :type exclude_empty_value: bool

    :return: True if the object was updated with new annotations; False
        otherwise.
    :rtype: bool
    """
    print(f"-->processing {obj}")
    updated = False
    if len(new_name) == 0:
        new_name = [""]

    if new_name[0] == "" and exclude_empty_value:
        return updated

    obj.setName(new_name[0])
    obj.save()
    updated = True

    return updated


def link_file_ann(conn, obj_to_link, file_ann):
    """
    Link a File Annotation to a specified OMERO object if not already linked.

    :param conn: OMERO connection for server interaction.
    :type conn: omero.gateway.BlitzGateway
    :param obj_to_link: OMERO object to which the file annotation will be
        linked.
    :type obj_to_link: omero.model.<ObjectType>
    :param file_ann: File Annotation object to link to the OMERO object.
    :type file_ann: omero.model.FileAnnotation
    :return: The file annotation is linked directly within the OMERO database.
    :rtype: None
    """
    links = list(conn.getAnnotationLinks(
        obj_to_link.OMERO_CLASS,
        parent_ids=[obj_to_link.getId()],
        ann_ids=[file_ann.getId()]
    ))
    if len(links) == 0:
        obj_to_link.linkAnnotation(file_ann)


def run_script():
    """
    Execute the main OMERO import script for annotating OMERO objects
    from CSV.

    This function establishes a client connection, gathers user input
    parameters, and initializes a connection to the OMERO server to
    parse a CSV file for key-value pairs, tags, and other metadata
    to annotate objects in the OMERO database.

    :return: Sets output messages and result objects for OMERO client session.
    :rtype: None
    """
    # Cannot add fancy layout if we want auto fill and selct of object ID
    source_types = [
                    rstring("Project"), rstring("Dataset"), rstring("Image"),
                    rstring("Screen"), rstring("Plate"), rstring("Well"),
                    rstring("Acquisition"), rstring("Image"),
    ]

    # Duplicate Image for UI, but not a problem for script
    target_types = [
                    rstring("<selected>"), rstring("Project"),
                    rstring("- Dataset"), rstring("-- Image"),
                    rstring("Screen"), rstring("- Plate"),
                    rstring("-- Well"), rstring("-- Acquisition"),
                    rstring("--- Image")
    ]

    separators = ["guess", ";", ",", "TAB", "|"]

    client = scripts.client(
        'Rename from CSV',
        """
     Rename a list of target objects on OMERO from a CSV file.
     \t
     To use this script, please
     \t
     - run another script called 'Export to CSV...' located under 'Annotation_scripts' folder
     - Add a new column in the generated CSV file with the replacement text to rename objects with (no header is required)
     - Save the modified CSV
     - Run this script
 
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

        scripts.String(
            P_FILE_ANN, optional=True, grouping="1.3",
            description="If no file is provided, list of file IDs " +
                        "containing metadata to populate (one per ID). " +
                        "Otherwise, takes the most recent CSV " +
                        "on each parent object."),

        scripts.Bool(
            "Other parameters", optional=True, grouping="2", default=True,
            description="Ticking or unticking this has no effect"),

        scripts.String(
            P_CSVSEP, optional=True, grouping="2.1",
            description="Separator used in the CSV file. 'guess' will " +
                        "attempt to detetect automatically which of " +
                        ",;\\t to use.",
            values=separators, default="guess"),

        scripts.Bool(
            P_EXCL_EMPTY, optional=True, grouping="2.2", default=True,
            description="Skip the keys with empty values."),

        scripts.List(
            P_EXCL_COL, optional=True, grouping="2.3",
            default="<ID>,<NAME>,<PARENTS>",
            description="Columns to exclude from the key-value pairs. " +
                        "<ID> and <NAME> correspond to the column name " +
                        "specified by the next two parameters. " +
                        "<PARENTS> matches all {PROJECT, DATASET, " +
                        "SCREEN, PLATE, RUN, WELL}.").ofType(rstring("")),

        scripts.String(
            P_TARG_COLID, optional=True, grouping="2.4",
            default="OBJECT_ID",
            description="The column name in the CSV containing " +
                        "the objects IDs."),

        scripts.String(
            P_TARG_COLNAME, optional=True, grouping="2.5",
            default="OBJECT_NAME",
            description="The column name in the CSV containing " +
                        "the objects names. (used only if the column " +
                        "ID is not found"),

        authors=["Christian Evenhuis", "Tom Boissonnet", "Jens Wendt", "Rémy Dornier"],
        institutions=["MIF UTS", "CAi HHU", "MiN WWU", "EPFL"],
        contact="https://forum.image.sc/tag/omero",
        version="1.0.0",
    )

    try:
        params = parameters_parsing(client)

        # wrap client to use the Blitz Gateway
        conn = BlitzGateway(client_obj=client)
        message, robj = main_loop(conn, params)
        client.setOutput("Message", rstring(message))
        if robj is not None:
            client.setOutput("Result", robject(robj._obj))

    except AssertionError as err:
        # Display assertion errors in OMERO.web activities
        client.setOutput("ERROR", rstring(err))
        raise AssertionError(str(err))

    finally:
        client.closeSession()


def parameters_parsing(client):
    """
    Parse and validate input parameters for the OMERO CSV import script.

    This function collects and prepares the input parameters provided by
    the client. It sets defaults for optional parameters, verifies the
    consistency of input values, and transforms certain parameters into
    appropriate formats for use in annotation.

    :param client: OMERO client providing the interface for parameter input.
    :type client: omero.scripts.client
    :return: Dictionary of parsed and validated input parameters, with
        defaults applied and necessary transformations made.
    :rtype: dict
    """
    params = {}
    # Param dict with defaults for optional parameters
    params[P_FILE_ANN] = None

    for key in client.getInputKeys():
        if client.getInput(key):
            params[key] = client.getInput(key, unwrap=True)

    if params[P_TARG_DTYPE] == "<selected>":
        params[P_TARG_DTYPE] = params[P_DTYPE]
    elif params[P_TARG_DTYPE].startswith("-"):
        # Getting rid of the trailing '---' added for the UI
        params[P_TARG_DTYPE] = params[P_TARG_DTYPE].split(" ")[1]

    assert params[P_TARG_DTYPE] in ALLOWED_PARAM[params[P_DTYPE]], \
           (f"{params['Target Data_Type']} is not a valid target for " +
            f"{params['Data_Type']}.")

    if params[P_DTYPE] == "Tag":
        assert params[P_FILE_ANN] is not None, \
            "File annotation ID must be given when using Tag as source"

    if ((params[P_FILE_ANN]) is not None
            and ("," in params[P_FILE_ANN])):
        # List of ID provided, have to do the split
        params[P_FILE_ANN] = params[P_FILE_ANN].split(",")
    else:
        params[P_FILE_ANN] = [int(params[P_FILE_ANN])]
    if len(params[P_FILE_ANN]) == 1:
        # Poulate the parameter with None or same ID for all source
        params[P_FILE_ANN] *= len(params[P_IDS])
        params["File_Annotation_multiplied"] = True
    params[P_FILE_ANN] = list(map(int, params[P_FILE_ANN]))

    assert len(params[P_FILE_ANN]) == len(params[P_IDS]), \
        "Number of IDs and FileAnnotation IDs must match"

    # Replacing the placeholders <ID> and <NAME> with values from params
    to_exclude = list(map(lambda x: x.replace('<ID>',
                                              params[P_TARG_COLID]),
                          params[P_EXCL_COL]))
    to_exclude = list(map(lambda x: x.replace('<NAME>',
                                              params[P_TARG_COLNAME]),
                          to_exclude))
    if "<PARENTS>" in to_exclude:
        to_exclude.remove("<PARENTS>")
        to_exclude.extend(["PROJECT", "DATASET", "SCREEN",
                           "PLATE", "PLATEACQUISITION", "WELL"])

    params[P_EXCL_COL] = to_exclude

    print("Input parameters:")
    keys = [P_DTYPE, P_IDS, P_TARG_DTYPE, P_FILE_ANN, P_CSVSEP,
            P_EXCL_COL, P_TARG_COLID, P_TARG_COLNAME, P_EXCL_EMPTY]

    for k in keys:
        print(f"\t- {k}: {params[k]}")
    print("\n####################################\n")

    if params[P_CSVSEP] == "guess":
        params[P_CSVSEP] = None
    elif params[P_CSVSEP] == "TAB":
        params[P_CSVSEP] = "\t"

    if params[P_DTYPE] == "Tag":
        params[P_DTYPE] = "TagAnnotation"
    if params[P_TARG_DTYPE] == "Acquisition":
        params[P_TARG_DTYPE] = "PlateAcquisition"

    return params


if __name__ == "__main__":
    run_script()
