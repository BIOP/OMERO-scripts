"""
 Move_to_group.py
 Transfer images from one group to another
-----------------------------------------------------------------------------
  Copyright (C) 2026
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
from omero.gateway import BlitzGateway
from omero.plugins.sessions import SessionsControl
from omero.plugins.chgrp import ChgrpControl
from omero.cli import CLI
from omero.rtypes import rlong, rstring, robject

# constant for the UI
P_DATA_TYPE = "Data_Type"
P_IDS = "IDs"
P_GROUP = "Target group"
P_TARGET = "Target object"


NOT_HANDLED_OBJECTS = ["Session", "Namespace", "Node", "LightPath", "Job", "OriginalFile", "Experimenter",
                       "ExperimenterGroup", "Reagent", "Annotation", "LightSource", "FolderImage, FolderRoi"]

# all class of supported objects
INSTRUMENT_CLASS = "Instrument"
DETECTOR_CLASS = "Detector"
DICHROIC_CLASS = "Dichroic"
FILTER_CLASS = "Filter"
OBJECTIVE_CLASS = "Objective"
CHANNEL_CLASS = "Channel"
ROI_CLASS = "Roi"
SHAPE_CLASS = "Shape"
FILESET_CLASS = "Fileset"
IMAGE_CLASS = "Image"
DATASET_CLASS = "Dataset"
PROJECT_CLASS = "Project"
SCREEN_CLASS = "Screen"
PLATE_CLASS = "Plate"
WELL_SAMPLE_CLASS = "WellSample"
WELL_CLASS = "Well"
PA_CLASS = "PlateAcquisition"

# dict of object_type:object
parentObject = {
    PROJECT_CLASS: omero.gateway.ProjectWrapper,
    DATASET_CLASS: omero.gateway.DatasetWrapper,
    IMAGE_CLASS: omero.gateway.ImageWrapper,
    SCREEN_CLASS: omero.gateway.ScreenWrapper,
    PLATE_CLASS: omero.gateway.PlateWrapper,
    PA_CLASS: omero.gateway.PlateAcquisitionWrapper,
    WELL_CLASS: omero.gateway.WellWrapper,
    FILESET_CLASS: omero.gateway.FilesetWrapper,
    INSTRUMENT_CLASS: omero.gateway.InstrumentWrapper,
    ROI_CLASS: omero.gateway.RoiWrapper,
    CHANNEL_CLASS: omero.gateway.ChannelWrapper,
    OBJECTIVE_CLASS: omero.gateway.ObjectiveWrapper,
    FILTER_CLASS: omero.gateway.FilterWrapper,
    DICHROIC_CLASS: omero.gateway.DichroicWrapper,
    DETECTOR_CLASS: omero.gateway.DetectorWrapper,
    SHAPE_CLASS: omero.gateway.ShapeWrapper
}

# dict of parent-children relationship
CHILD_OBJECTS = {
    PROJECT_CLASS: DATASET_CLASS,
    DATASET_CLASS: IMAGE_CLASS,
    SCREEN_CLASS: PLATE_CLASS,
    PLATE_CLASS: WELL_CLASS,
    WELL_CLASS: WELL_SAMPLE_CLASS,
    WELL_SAMPLE_CLASS: IMAGE_CLASS
}

# current omero server for the CLI call
OMERO_SERVER = "omero-server-poc.epfl.ch"
PORT = "4064"


def get_children_recursive(conn, source_object, target_object_type, object_type_ids_dict):
    """
    List all containers and images which are linked to tag(s)

    Parameters
    ----------
    conn: ``omero.gateway.BlitzGateway`` object
        OMERO connection.
    source_object: omero.gateway.ObjectWrapper
        OMERO source object.
    target_object_type: str
        Target object type where the children recursion stops i.e. Image
    object_type_ids_dict: dict
        Dictionary of [object_type]:[list of all object ids for the current object_type]

    Returns
    -------
    object_type_ids_dict: dict
        Dictionary of [object_type]:[list of all object ids for the current object_type]
    """
    # adding source object ids
    if source_object.OMERO_CLASS in object_type_ids_dict:
        object_type_ids_dict[source_object.OMERO_CLASS].append(str(source_object.getId()))
    else:
        object_type_ids_dict[source_object.OMERO_CLASS] = [str(source_object.getId())]

    # adding image attribute object ids
    if source_object.OMERO_CLASS == target_object_type:
        return get_image_attributes(conn, source_object, object_type_ids_dict)

    # Stop condition, we return the source_obj children
    if source_object.OMERO_CLASS != WELL_SAMPLE_CLASS:
        child_objs = source_object.listChildren()
    else:
        child_objs = [source_object.getImage()]

    for child_obj in child_objs:
        # Going down in the Hierarchy list
        get_children_recursive(conn, child_obj, target_object_type, object_type_ids_dict)

    return object_type_ids_dict


def get_image_attributes(conn, image_object, object_type_ids_dict):
    """
    List all objects, linked to the current image, which are linked to tag(s)

    Parameters
    ----------
    conn: ``omero.gateway.BlitzGateway`` object
        OMERO connection.
    image_object: omero.gateway.ImageWrapper
        OMERO image object.
    object_type_ids_dict: dict
        Dictionary of [object_type]:[list of all object ids for the current object_type]

    Returns
    -------
    object_type_ids_dict: dict
        Dictionary of [object_type]:[list of all object ids for the current object_type]
    """
    # get the image instrument
    instrument_obj = image_object.getInstrument()

    if instrument_obj is not None:
        if INSTRUMENT_CLASS in object_type_ids_dict:
            object_type_ids_dict[INSTRUMENT_CLASS].append(str(instrument_obj.getId()))
        else:
            object_type_ids_dict[INSTRUMENT_CLASS] = [str(instrument_obj.getId())]

        det_ids = [str(det.getId()) for det in instrument_obj.getDetectors() if det is not None]
        if DETECTOR_CLASS in object_type_ids_dict:
            object_type_ids_dict[DETECTOR_CLASS] = object_type_ids_dict[DETECTOR_CLASS] + det_ids
        else:
            object_type_ids_dict[DETECTOR_CLASS] = det_ids

        dic_ids = [str(dic.getId()) for dic in instrument_obj.getDichroics() if dic is not None]
        if DICHROIC_CLASS in object_type_ids_dict:
            object_type_ids_dict[DICHROIC_CLASS] = object_type_ids_dict[DICHROIC_CLASS] + dic_ids
        else:
            object_type_ids_dict[DICHROIC_CLASS] = dic_ids

        flt_ids = [str(flt.getId()) for flt in instrument_obj.getFilters() if flt is not None]
        if FILTER_CLASS in object_type_ids_dict:
            object_type_ids_dict[FILTER_CLASS] = object_type_ids_dict[FILTER_CLASS] + flt_ids
        else:
            object_type_ids_dict[FILTER_CLASS] = flt_ids

        obj_ids = [str(obj.getId()) for obj in instrument_obj.getObjectives() if obj is not None]
        if OBJECTIVE_CLASS in object_type_ids_dict:
            object_type_ids_dict[OBJECTIVE_CLASS] = object_type_ids_dict[OBJECTIVE_CLASS] + obj_ids
        else:
            object_type_ids_dict[OBJECTIVE_CLASS] = obj_ids


    if image_object is not None:
        if FILESET_CLASS in object_type_ids_dict:
            object_type_ids_dict[FILESET_CLASS].append(str(image_object.getFileset().getId()))
        else:
            object_type_ids_dict[FILESET_CLASS] = [str(image_object.getFileset().getId())]

        ch_ids = [str(ch.getId()) for ch in image_object.getChannels()]
        if CHANNEL_CLASS in object_type_ids_dict:
            object_type_ids_dict[CHANNEL_CLASS] = object_type_ids_dict[CHANNEL_CLASS] + ch_ids
        else:
            object_type_ids_dict[CHANNEL_CLASS] = ch_ids


    # get roi & shape tags
    roi_service = conn.getRoiService()
    result = roi_service.findByImage(image_object.getId(), None)
    roi_ids = []
    shape_ids = []
    for roi in result.rois:
        roi_ids.append(str(roi.getId().getValue()))
        shape_ids = shape_ids + [str(s.getId().getValue()) for s in roi.copyShapes()]

    if ROI_CLASS in object_type_ids_dict:
        object_type_ids_dict[ROI_CLASS] = object_type_ids_dict[ROI_CLASS] + roi_ids
    else:
        object_type_ids_dict[ROI_CLASS] = roi_ids

    if SHAPE_CLASS in object_type_ids_dict:
        object_type_ids_dict[SHAPE_CLASS] = object_type_ids_dict[SHAPE_CLASS] + shape_ids
    else:
        object_type_ids_dict[SHAPE_CLASS] = shape_ids

    return object_type_ids_dict


def list_tag_attached(conn, qs, params, src_group_tags, object_type_ids_dict):
    """
     List tag ids linked to current objects and returns dictionaries matching object, tags and links

    Parameters
    ----------
    conn: ``omero.gateway.BlitzGateway`` object
        OMERO connection.
    qs: ``omero.gateway.ProxyObjectWrapper`` object
        OMERO service to query the database
    params: ``omero.sys.ParametersI`` object
        Current context parameters for queries
    src_group_tags: dict
        Dictionary of [src_tag_id]:[src_object]
    object_type_ids_dict: dict
        Dictionary of [object_type]:[list of all object ids for the current object_type]

    Returns
    -------
    object_tag_dict: dict
        Dictionary of [object_type:object_id]:[list of source_tag_ids linked to the current object]
    object_dict: dict
        Dictionary of [object_type]:[list of all object ids of the current object_type]
    tag_set: set
        unique list of all tags linked to the current objects
    annotation_link_dict: dict
        Dictionary of [object_type]:[list of object-tag-link-id for the current object_type]

    """
    object_tag_dict = {}
    object_dict = {}
    annotation_link_dict = {}
    tag_set = set()

    # get the list of source tag ids
    src_group_tags_ids = src_group_tags.keys()
    src_group_tags_ids = [str(img_id) for img_id in src_group_tags_ids]

    for object_type, ids_list in object_type_ids_dict.items():
        # exclude well sample because it is not annotatable
        if object_type == WELL_SAMPLE_CLASS:
            continue

        print(f"Getting tags & object links for {len(set(ids_list))} {object_type}")
        (tags_on_obj, objects_ids, unique_tags, obj_annotation_link_dic) = get_tag_attached(conn, qs, params,
                                                                                            object_type, set(ids_list),
                                                                                            src_group_tags_ids)
        object_tag_dict = {**object_tag_dict, **tags_on_obj}
        object_dict[object_type] = objects_ids
        annotation_link_dict = {**annotation_link_dict, **obj_annotation_link_dic}
        tag_set = tag_set.union(unique_tags)

    return object_tag_dict, object_dict, tag_set, annotation_link_dict


def get_tag_attached(conn, qs, params, target, target_ids, tag_ids):
    """
    List tag ids linked to current objects and returns dictionaries matching object, tags and links

    Parameters
    ----------
    conn: ``omero.gateway.BlitzGateway`` object
        OMERO connection.
    qs: ``omero.gateway.ProxyObjectWrapper`` object
        OMERO service to query the database
    params: ``omero.sys.ParametersI`` object
        Current context parameters for queries
    target: str
        Object type name (ex: Image)
    target_ids: list
        all ids referring to the same object type
    tag_ids: list
        all available tags in the source group

    Returns
    -------
    target_tag_dict: dict
        Dictionary of [object_type:object_id]:[list of source_tag_ids linked to the current object]
    annotated_objects_ids_list: list
        all ids of objects of the current type which are linked to at least one tag
    tag_set: set
        unique list of all tags linked to the current objects
    annotation_link_dict: dict
        Dictionary of [object_type]:[list of object-tag-link-id for the current object_type]

    """
    target_tag_dict = {}
    annotation_link_dict = {}
    annotated_objects_ids_list = []
    tag_set = set()

    # if no target, return empty dic
    if len(target_ids) == 0:
        return target_tag_dict, annotated_objects_ids_list, tag_set, annotation_link_dict

    q = (f"select link.id, link.parent.id, link.child.id from {target}AnnotationLink link left outer join fetch "
         f"link.parent.details where link.parent.id in ({','.join(target_ids)}) and link.child.id "
         f"in ({','.join(tag_ids)})")
    print(f"Query used : {q}")
    results = qs.projection(q, params, conn.SERVICE_OPTS)

    for result in results:
        # get an exhaustive list of tag ids linked to the current object type
        tag_set.add(result[2].val)

        # populate a dict of which object is annotated with which tag(s)
        if f"{target}:{result[1].val}" not in target_tag_dict:
            target_tag_dict[f"{target}:{result[1].val}"] = [result[2].val]
            annotated_objects_ids_list.append(result[1].val)
        else:
            target_tag_dict[f"{target}:{result[1].val}"].append(result[2].val)

        # populate dict with AnnotationLink object ids to later delete them
        if target not in annotation_link_dict:
            annotation_link_dict[target] = [result[0].val]
        else:
            annotation_link_dict[target].append(result[0].val)

    return target_tag_dict, annotated_objects_ids_list, tag_set, annotation_link_dict


def get_all_tags(conn, group_id):
    """
    Gets a dict of all existing tag_id within the current group with their respective object as values

    Parameters:
    --------------
    conn : ``omero.gateway.BlitzGateway`` object
        OMERO connection.

    Returns:
    -------------
    tag_dict: dict
        Dictionary of [tag_id]:[tag_object]
    """
    tag_list = conn.getObjects("TagAnnotation", opts={'group': group_id})
    tag_dict = {}
    for tag in tag_list:
        tag_dict[tag.getId()] = tag
    return tag_dict


def get_objects_by_query(conn, qs, params, target, target_ids):
    """
    Returns a list of objects within the current group fetched from the database

    Parameters
    ----------
    conn: ``omero.gateway.BlitzGateway`` object
        OMERO connection.
    qs: ``omero.gateway.ProxyObjectWrapper`` object
        OMERO service to query the database
    params: ``omero.sys.ParametersI`` object
        Current context parameters for queries
    target: str
        Object type name (ex: Image)
    target_ids: list
        all ids referring to the same object type

    Returns
    -------
    object_list: list
        The list of all objects of type 'target'
    """
    object_list = []

    # if no target, return empty dic
    if len(target_ids) == 0:
        return object_list

    q = f"select obj from {target} obj where obj.id in ({','.join(target_ids)})"
    print(f"Query used : {q}")
    results = qs.findAllByQuery(q, params, conn.SERVICE_OPTS)

    for result in results:
        object_list.append(parentObject[target](conn, result))

    return object_list



def get_existing_tags_or_create_new_tags(conn, target_tags, source_tags, list_of_src_tag_ids_to_match):
    """
    Check the existence of source tags in the target group.
    If the source tags already exist in the target group, get them.
    Otherwise, create them.

    Parameters
    ----------
    conn: ``omero.gateway.BlitzGateway`` object
        OMERO connection.
    target_tags: dict
        Dictionary in the format [tgt_tag_id]:[tgt_tag_object]
    source_tags: dict
        Dictionary of [src_tag_id]:[src_tag_object]
    list_of_src_tag_ids_to_match: list
        Source tag ids linked to the objects to move

    Returns
    -------
    src_tag_tgt_tag_dict: dict
        Dictionary of [source_tag_id]:[target_tag_id]

    """
    src_tag_tgt_tag_dict = {}

    # get the dic of the target tag names
    target_tags_names_dic = {}
    for target_tag_id, target_tag in target_tags.items():
        target_tags_names_dic[target_tag.getTextValue().lower()] = target_tag_id

    for tag_id in list_of_src_tag_ids_to_match:
        # get the source tag name
        available_tag_name = source_tags[tag_id].getTextValue()

        # if the tag already exists in the target group, pick it
        # otherwise, create a new one
        if available_tag_name.lower() in target_tags_names_dic:
            src_tag_tgt_tag_dict[tag_id] = target_tags_names_dic[available_tag_name.lower()]
        else:
            duplicate_tag_ann = create_tag(conn, available_tag_name)
            target_tags_names_dic[duplicate_tag_ann.getTextValue().lower()] = duplicate_tag_ann.getId()
            target_tags[duplicate_tag_ann.getId()] = duplicate_tag_ann
            src_tag_tgt_tag_dict[tag_id] = duplicate_tag_ann.getId()

    return src_tag_tgt_tag_dict


def create_tag(conn, tag_name):
    """
    Create a new tag with given name

    Parameters
    ----------
    conn: ``omero.gateway.BlitzGateway`` object
        OMERO connection.
    tag_name: str
        Name of the tag to create

    Returns
    -------
    tag_ann: ``omero.gateway.TagAnnotationWrapper`` object
        the tag object

    """
    tag_ann = omero.gateway.TagAnnotationWrapper(conn)
    tag_ann.setValue(tag_name)
    tag_ann.save()
    return tag_ann


def link_tags_back(conn, qs, params, object_tag_dict, object_dict, tgt_tag_dict, src_tag_tgt_tag_dict):
    """
    Links the tags to the corresponding objects in the target group
    
    Parameters
    ----------
    conn: ``omero.gateway.BlitzGateway`` object
        OMERO connection.
    qs: ``omero.gateway.ProxyObjectWrapper`` object
        OMERO service to query the database
    params: ``omero.sys.ParametersI`` object
        Current context parameters for queries
    object_tag_dict: dict
        Dictionary of [object_type:object_id]:[list of source_tag_ids linked to the current object]
    object_dict: dict
        Dictionary of [object_type]:[list of all object ids of the same type]
    tgt_tag_dict: dict
        Dictionary of [target_tag_id]:[target_tag_object] available for the current group
    src_tag_tgt_tag_dict: dict
        Dictionary of [source_tag_id]:[target_tag_id]

    Returns
    -------

    """
    for obj_type, object_ids_list in object_dict.items():
        # converts object ids to string for query
        str_object_ids_list = [str(obj_id) for obj_id in object_ids_list]

        # get the object from the target group
        target_objects = get_objects_by_query(conn, qs, params, obj_type, str_object_ids_list)

        for target_object in target_objects:
            src_tag_id_list = object_tag_dict[f"{obj_type}:{target_object.getId()}"]

            # loop over all tags attached to the current parent
            for src_tag_id in src_tag_id_list:
                # get the target tag id
                tgt_tag_id = src_tag_tgt_tag_dict[src_tag_id]

                # don't link twice the same tag to the same parent
                if tgt_tag_id != src_tag_id:
                    tgt_tag = tgt_tag_dict[tgt_tag_id]
                    target_object.linkAnnotation(tgt_tag)


def move_to_group(conn, target, target_ids, current_group, target_group_id, target_group_name):
    """
    Call the omero.plugin.chgrp using the CLI, to move the selected objects to the selected group

    Parameters
    ----------
    conn: ``omero.gateway.BlitzGateway`` object
        OMERO connection.
    target: str
        parent container type to move
    target_ids: list of str
        list of object IDs, of type 'target'
    current_group: ``omero.gateway.GroupWrapper`` object
        the current OMERO group object
    target_group_id: int
        the id of the target group
    target_group_name:`str
        the name of the target group

    Returns
    -------
    err: Exception
        the exception object. None if no error occurred.
    """
    import_args = ["chgrp",
                   "Group:%s" % target_group_id,
                   f"{target}:{','.join(target_ids)}",
                   "--include", "Annotation"
                   ]
    # open cli connection
    with omero.cli.cli_login("-k", "%s" % conn.c.getSessionId(), "-s", OMERO_SERVER, "-p", PORT) as cli:
        cli.register('chgrp', ChgrpControl, '_')
        cli.register('sessions', SessionsControl, '_')
        # launch move to group
        try:
            print(" ".join(import_args))
            cli.invoke(import_args, strict=True)
            print("SUCCESS", f"Moved from group '{current_group.getName()}' to group '{target_group_name}'")
        except PermissionError as err:
            message = f"Error during moving {target}:{target_ids} " \
                      f"from group {current_group.getName()} to group {target_group_name} : {err}"
            print(message)
            cli.get_client().closeSession()
            cli.close()
            return err
        cli.get_client().closeSession()

    return None


def main_loop(conn: BlitzGateway, script_params):
    """
    Move the selected objects and their child to the selected group.
    Tags are included in the transfer.

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
    source_object : omero.gateway.ObjectWrapper
        One source object in the target group
    err: Exception
        In case an error was caught
    """

    # Image ids
    source_object_type = script_params[P_DATA_TYPE]
    source_ids = script_params[P_IDS]
    target_object_type = script_params[P_TARGET]

    # target group
    target_group_name = script_params[P_GROUP]
    try:
        group_id = int(target_group_name)
        target_group_name = conn.getObject("ExperimenterGroup", group_id).name
        print(f"User provided a group id {group_id}, which correspond to group {target_group_name}")
    except ValueError:
        print(f"User provided a group name: {target_group_name}")
    current_group = conn.getGroupFromContext()
    excluded_groups = [0, 1, 2]

    params = omero.sys.ParametersI()
    qs = conn.getQueryService()

    # check if the connected user is part of the target group
    available_groups = [g.name.lower() for g in conn.listGroups() if g.id not in excluded_groups]

    if target_group_name.lower() not in available_groups:
        message = f"ERROR : You are not part of the group {target_group_name}. You cannot transfer images to that group"
        print(message)
        return message, None, PermissionError(message)

    # get the target group
    target_group = [g for g in conn.listGroups()
                    if g.id not in excluded_groups and g.name.lower() == target_group_name.lower()][0]
    target_group_name = target_group.getName()
    target_group_id = target_group.getId()

    # throw error if the 2 groups are identical
    if current_group.getId() == target_group.getId():
        message = f"ERROR : You cannot transfer images to the same group (current and target group are the same)"
        print(message)
        return message, None, PermissionError(message)

    # get all available tag from source group
    src_group_tags = get_all_tags(conn, current_group.getId())

    # get all objects ids to scan
    object_type_id_dic = {}
    source_objects = conn.getObjects(source_object_type, source_ids)

    if source_objects is None:
        message = f"ERROR : No objects found for {source_object_type} : {','.join(source_ids)}"
        print(message)
        return message, None, PermissionError(message)

    for source_object in source_objects:
        object_type_id_dic = get_children_recursive(conn, source_object, target_object_type, object_type_id_dic)
        final_object = source_object

    # get all tags linked to objects
    object_tag_dic, object_dic, tag_list, tag_annotation_link_dic = list_tag_attached(conn, qs, params, src_group_tags, object_type_id_dic)

    # remove current tag links to current objects
    # may crash at some point due to https://forum.image.sc/t/how-to-delete-specific-omero-objects/119714
    for obj_type, ann_link_dic in tag_annotation_link_dic.items():
        conn.deleteObjects(f"{obj_type}AnnotationLink", ann_link_dic, wait=True)

    # move to the target group
    source_ids = [str(img_id) for img_id in source_ids]
    err = move_to_group(conn, source_object_type, source_ids, current_group, target_group_id, target_group_name)
    if err:
        return f"Error hen moving data to group {target_group_name}. Please look at logs", None, err

    # switch to target group
    conn.SERVICE_OPTS.setOmeroGroup(target_group_id)
    qs = conn.getQueryService()
    params = omero.sys.ParametersI()

    # get all available tag from target group
    tgt_group_tags = get_all_tags(conn, target_group_id)

    # get target tags or create new ones
    src_tag_tgt_tag_dic = get_existing_tags_or_create_new_tags(conn, tgt_group_tags, src_group_tags, tag_list)

    # link duplicate tags back to corresponding objects
    link_tags_back(conn, qs, params, object_tag_dic, object_dic, tgt_group_tags, src_tag_tgt_tag_dic)

    return f"Successful transfer of data to group {target_group_name}", final_object, None


def run_script():
    source_types = [
        rstring(PROJECT_CLASS), rstring(DATASET_CLASS), rstring(IMAGE_CLASS),
        rstring(SCREEN_CLASS), rstring(PLATE_CLASS)
    ]

    client = scripts.client(
        'Move to group',
        f"""
    This script moves the selected data from the current group to the selected one. 
    Tags linked to any object are included in the transfer except those linked to the following objects : 
    {','.join(NOT_HANDLED_OBJECTS)}'
    \t
    Warning: the user who is running the script HAS TO BE a group owner.
    \t
    Warning: if you have a dataset with multiple image owners, then, you need to change the ownership of the images 
    to have the same owner for all the transferred data.
    \t
    Warning: Please be aware that annotations that you don't own (kvp, attachments and other) will not be moved.
    Consider changing the ownership of annotations before running the script.
    \t
        """,
        scripts.String(
            P_DATA_TYPE, optional=False,grouping="1",
            description="Source objects",
            values=source_types, default="Image"),

        scripts.List(
            P_IDS,  optional=False, grouping="2",
            description="Objects IDs"
                        " group.").ofType(rlong(0)),
        scripts.String(
            P_GROUP, optional=False, grouping="3",
            description="Target group, name or ID"),

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
        script_params[P_TARGET] = IMAGE_CLASS

        # wrap client to use the Blitz Gateway
        conn = BlitzGateway(client_obj=client)
        current_group = conn.getGroupFromContext()
        print("script params")
        for k, v in script_params.items():
            print(k, v)
        message, res_obj, err = main_loop(conn, script_params)
        client.setOutput("Message", rstring(message))
        if res_obj is not None:
            client.setOutput("Result", robject(res_obj._obj))
        if err is not None:
            conn.setGroupForSession(current_group.getId())
            client.setOutput("ERROR", rstring(err))

    except AssertionError as err:
        client.setOutput("ERROR", rstring(err))
        raise AssertionError(str(err))
    finally:
        client.closeSession()


if __name__ == "__main__":
    run_script()
