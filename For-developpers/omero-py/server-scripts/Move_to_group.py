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


Warning: the user who is running the script must be a group owner

Warning: if you have a dataset with multiple image owners, then, you need to change the owner to have only one
Please be aware that annotations that you don't own (kvp, attachments and other) will not be moved.
Consider changing the ownership before running the script


"""
import traceback

import omero
import omero.scripts as scripts
import omero.model as model
from omero.gateway import BlitzGateway
from omero.gateway import DatasetWrapper
from omero.gateway import MapAnnotationWrapper
from omero.plugins.sessions import SessionsControl
from omero.plugins.chgrp import ChgrpControl
from omero.cli import CLI
from omero.rtypes import rlong, rstring, robject
from datetime import datetime


P_DATA_TYPE = "Data_Type"
P_IDS = "IDs"
P_GROUP = "Target group"
P_TARGET = "Target object"

CONTAINER_OBJECTS = ["Project", "Dataset", "Image", "Screen", "Plate", "PlateAcquisition", "Well"]
IMAGE_LINKED_OBJECTS = ["Fileset", "Instrument", "Roi", "Channel"]
INSTRUMENT_LINKED_OBJECTS = ["Objective",  "Filter", "Dichroic", "Detector"]
ROI_LINKED_OBJECTS = ["Shape"]

NOT_HANDLED_OBJECTS = ["Session", "Namespace", "Node", "LightPath", "Job", "OriginalFile", "Experimenter",
                       "ExperimenterGroup", "Reagent", "Annotation", "LightSource", "FolderImage, FolderRoi"]

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

CHILD_OBJECTS = {
    PROJECT_CLASS: DATASET_CLASS,
    DATASET_CLASS: IMAGE_CLASS,
    SCREEN_CLASS: PLATE_CLASS,
    PLATE_CLASS: WELL_CLASS,
    WELL_CLASS: WELL_SAMPLE_CLASS,
    WELL_SAMPLE_CLASS: IMAGE_CLASS
}

OMERO_SERVER = "omero-server-poc.epfl.ch"
OMERO_WEBSERVER = "omero-poc.epfl.ch"
PORT = "4064"


def get_children_recursive(conn, source_object, target_object_type, object_type_ids_dic):
    # adding source object ids
    if source_object.OMERO_CLASS in object_type_ids_dic:
        object_type_ids_dic[source_object.OMERO_CLASS].append(str(source_object.getId()))
    else:
        object_type_ids_dic[source_object.OMERO_CLASS] = [str(source_object.getId())]

    if source_object.OMERO_CLASS == target_object_type:
        return get_image_attributes(conn, source_object, object_type_ids_dic)

    # Stop condition, we return the source_obj children
    if source_object.OMERO_CLASS != WELL_SAMPLE_CLASS:
        child_objs = source_object.listChildren()
    else:
        child_objs = [source_object.getImage()]

    for child_obj in child_objs:
        # Going down in the Hierarchy list
        get_children_recursive(conn, child_obj, target_object_type, object_type_ids_dic)

    return object_type_ids_dic


def get_image_attributes(conn, image_object, object_type_ids_dic):
    instrument_obj = image_object.getInstrument()

    if instrument_obj is not None:
        if INSTRUMENT_CLASS in object_type_ids_dic:
            object_type_ids_dic[INSTRUMENT_CLASS].append(str(instrument_obj.getId()))
        else:
            object_type_ids_dic[INSTRUMENT_CLASS] = [str(instrument_obj.getId())]

        det_ids = [str(det.getId()) for det in instrument_obj.getDetectors() if det is not None]
        if DETECTOR_CLASS in object_type_ids_dic:
            object_type_ids_dic[DETECTOR_CLASS] = object_type_ids_dic[DETECTOR_CLASS] + det_ids
        else:
            object_type_ids_dic[DETECTOR_CLASS] = det_ids

        dic_ids = [str(dic.getId()) for dic in instrument_obj.getDichroics() if dic is not None]
        if DICHROIC_CLASS in object_type_ids_dic:
            object_type_ids_dic[DICHROIC_CLASS] = object_type_ids_dic[DICHROIC_CLASS] + dic_ids
        else:
            object_type_ids_dic[DICHROIC_CLASS] = dic_ids

        flt_ids = [str(flt.getId()) for flt in instrument_obj.getFilters() if flt is not None]
        if FILTER_CLASS in object_type_ids_dic:
            object_type_ids_dic[FILTER_CLASS] = object_type_ids_dic[FILTER_CLASS] + flt_ids
        else:
            object_type_ids_dic[FILTER_CLASS] = flt_ids

        obj_ids = [str(obj.getId()) for obj in instrument_obj.getObjectives() if obj is not None]
        if OBJECTIVE_CLASS in object_type_ids_dic:
            object_type_ids_dic[OBJECTIVE_CLASS] = object_type_ids_dic[OBJECTIVE_CLASS] + obj_ids
        else:
            object_type_ids_dic[OBJECTIVE_CLASS] = obj_ids


    if image_object is not None:
        if FILESET_CLASS in object_type_ids_dic:
            object_type_ids_dic[FILESET_CLASS].append(str(image_object.getFileset().getId()))
        else:
            object_type_ids_dic[FILESET_CLASS] = [str(image_object.getFileset().getId())]

        ch_ids = [str(ch.getId()) for ch in image_object.getChannels()]
        if CHANNEL_CLASS in object_type_ids_dic:
            object_type_ids_dic[CHANNEL_CLASS] = object_type_ids_dic[CHANNEL_CLASS] + ch_ids
        else:
            object_type_ids_dic[CHANNEL_CLASS] = ch_ids


    # get roi & shape tags
    roi_service = conn.getRoiService()
    result = roi_service.findByImage(image_object.getId(), None)
    roi_ids = []
    shape_ids = []
    for roi in result.rois:
        roi_ids.append(str(roi.getId().getValue()))
        shape_ids = shape_ids + [str(s.getId().getValue()) for s in roi.copyShapes()]

    if ROI_CLASS in object_type_ids_dic:
        object_type_ids_dic[ROI_CLASS] = object_type_ids_dic[ROI_CLASS] + roi_ids
    else:
        object_type_ids_dic[ROI_CLASS] = roi_ids

    if SHAPE_CLASS in object_type_ids_dic:
        object_type_ids_dic[SHAPE_CLASS] = object_type_ids_dic[SHAPE_CLASS] + shape_ids
    else:
        object_type_ids_dic[SHAPE_CLASS] = shape_ids

    return object_type_ids_dic


def list_tag_attached(conn, qs, params, src_group_tags, object_type_ids_dic):
    objects_tagged_map = {}
    tag_target_count_dict = {}
    objects_map = {}
    tag_annotation_link_dic = {}
    tag_set = set()

    available_tags_src_group_ids = src_group_tags.keys()
    available_tags_src_group_ids = [str(img_id) for img_id in available_tags_src_group_ids]

    for object_type, ids_list in object_type_ids_dic.items():
        if object_type == WELL_SAMPLE_CLASS:
            continue

        print(f"Getting tags & object links for {len(set(ids_list))} {object_type}")
        tags_on_inst, inst_objects_ids, unique_inst_tags, inst_annotation_link_dic = get_tag_attached(conn, qs, params,
                                                                                                      object_type,
                                                                                                      set(ids_list),
                                                                                                      available_tags_src_group_ids)
        objects_tagged_map = {**objects_tagged_map, **tags_on_inst}
        objects_map[object_type] = inst_objects_ids
        tag_annotation_link_dic = {**tag_annotation_link_dic, **inst_annotation_link_dic}
        tag_set = tag_set.union(unique_inst_tags)

    return objects_tagged_map, objects_map, tag_set, tag_target_count_dict, tag_annotation_link_dic


def get_tag_attached(conn, qs, params, target, target_ids, tag_ids):
    target_tag_dic = {}
    annotation_link_dic = {}
    annotated_objects_list = []
    tag_set = set()

    # if no target, return empty dic
    if len(target_ids) == 0:
        return target_tag_dic, annotated_objects_list, tag_set, annotation_link_dic

    q = f"select link.id, link.parent.id, link.child.id from {target}AnnotationLink link left outer join fetch link.parent.details where link.parent.id in ({','.join(target_ids)}) and link.child.id in ({','.join(tag_ids)})"
    print(f"Query used : {q}")
    results = qs.projection(q, params, conn.SERVICE_OPTS)

    for result in results:
        # get an exhaustive list of tag ids linked to the current object type
        tag_set.add(result[2].val)

        # populate a dic of which object is annotated with which tag(s)
        if f"{target}:{result[1].val}" not in target_tag_dic:
            target_tag_dic[f"{target}:{result[1].val}"] = [result[2].val]
            annotated_objects_list.append(result[1].val)
        else:
            target_tag_dic[f"{target}:{result[1].val}"].append(result[2].val)

        # populate dic with AnnotationLink object ids to later delete them
        if target not in annotation_link_dic:
            annotation_link_dic[target] = [result[0].val]
        else:
            annotation_link_dic[target].append(result[0].val)

    return target_tag_dic, annotated_objects_list, tag_set, annotation_link_dic


def get_all_tags(conn, group_id):
    """Gets a dict of all existing tag_id within the current group with their respective object as values

    Parameters:
    --------------
    conn : ``omero.gateway.BlitzGateway`` object
        OMERO connection.

    Returns:
    -------------
    tag_dict: dict
        Dictionary in the format {tag1.id:tag1, tag2.id:tag2, ...}
    """
    tag_list = conn.getObjects("TagAnnotation", opts={'group': group_id})
    tag_dict = {}
    for tag in tag_list:
        tag_dict[tag.getId()] = tag
    return tag_dict


def get_objects_by_query(conn, qs, params, target, target_ids):
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
    err: Exception
        In case an error was caught
    """

    # Image ids
    source_object_type = script_params[P_DATA_TYPE]
    source_ids = script_params[P_IDS]
    target_object_type = script_params[P_TARGET]

    # target group
    target_group_name = script_params[P_GROUP]
    current_group = conn.getGroupFromContext()
    excluded_groups = [0, 1, 2]

    params = omero.sys.ParametersI()
    qs = conn.getQueryService()

    # check if the connected user is part of the target group
    available_groups = [g.name.lower() for g in conn.listGroups() if g.id not in excluded_groups]

    if target_group_name.lower() not in available_groups:
        message = f"ERROR : You are not part of the group {target_group_name}. You cannot transfer images to that group"
        return message, None

    # get the target group
    target_group = [g for g in conn.listGroups()
                    if g.id not in excluded_groups and g.name.lower() == target_group_name.lower()][0]
    target_group_name = target_group.getName()

    # throw error if the 2 groups are identical
    if current_group.getId() == target_group.getId():
        message = f"ERROR : You cannot transfer images to the same group (current and target group are the same)"
        return message, None

    # get all available tag from source group
    src_group_tags = get_all_tags(conn, current_group.getId())

    # get all objects ids to scan
    object_type_id_dic = {}
    source_objects = conn.getObjects(source_object_type, source_ids)
    for source_object in source_objects:
        object_type_id_dic = get_children_recursive(conn, source_object, target_object_type, object_type_id_dic)

    # get all tags linked to objects
    object_tag_dic, object_dic, tag_list, tag_objects_count_dic, tag_annotation_link_dic = list_tag_attached(conn, qs, params, src_group_tags, object_type_id_dic)

    # remove current tag links to current objects
    # may crash at some point due to https://forum.image.sc/t/how-to-delete-specific-omero-objects/119714
    for obj_type, ann_link_dic in tag_annotation_link_dic.items():
        conn.deleteObjects(f"{obj_type}AnnotationLink", ann_link_dic, wait=True)

    # move to the target group
    source_ids = [str(img_id) for img_id in source_ids]
    move_to_group(conn, source_object_type, source_ids, current_group, target_group, target_group_name)

    # switch to target group
    conn.SERVICE_OPTS.setOmeroGroup(target_group.getId())
    qs = conn.getQueryService()
    params = omero.sys.ParametersI()

    # get all available tag from target group
    target_group_tags = get_all_tags(conn, target_group.getId())

    # get target tags or create new ones
    tag_new_tag_dic = get_existing_tags_or_create_new_tags(conn, target_group_tags, src_group_tags, tag_list)

    # link duplicate tags back to corresponding objects
    link_tags_back(conn, qs, params, object_tag_dic, object_dic, target_group_tags, tag_new_tag_dic)

    return "Great!", None


def get_existing_tags_or_create_new_tags(conn, target_tags, source_tags, available_tags_id_list):
    tag_new_tag_dic = {}

    # get the dic of the target tag names
    target_tags_names_dic = {}
    for target_tag_id, target_tag in target_tags.items():
        target_tags_names_dic[target_tag.getTextValue().lower()] = target_tag_id

    for tag_id in available_tags_id_list:
        # get the source tag name
        available_tag_name = source_tags[tag_id].getTextValue()

        # if the tag already exists in the target group, pick it
        # otherwise, create a new one
        if available_tag_name.lower() in target_tags_names_dic:
            tag_new_tag_dic[tag_id] = target_tags_names_dic[available_tag_name.lower()]
        else:
            duplicate_tag_ann = create_tag(conn, available_tag_name)
            target_tags_names_dic[duplicate_tag_ann.getTextValue().lower()] = duplicate_tag_ann.getId()
            target_tags[duplicate_tag_ann.getId()] = duplicate_tag_ann
            tag_new_tag_dic[tag_id] = duplicate_tag_ann.getId()

    return tag_new_tag_dic


def create_tag(conn, tag_name):
    tag_ann = omero.gateway.TagAnnotationWrapper(conn)
    tag_ann.setValue(tag_name)
    tag_ann.save()
    return tag_ann


def link_tags_back(conn, qs, params, object_tag_dic, object_dic, tag_dic, tag_new_tag_dic):

    for obj_type, object_ids_list in object_dic.items():
        str_object_ids_list = [str(obj_id) for obj_id in object_ids_list] # converts to string for query
        target_objects = get_objects_by_query(conn, qs, params, obj_type, str_object_ids_list)
        for target_object in target_objects:
            tag_id_list = object_tag_dic[f"{obj_type}:{target_object.getId()}"]

            # loop over all tags attached to the current parent
            for tag_id in tag_id_list:
                # get the duplicated tag
                dup_tag_id = tag_new_tag_dic[tag_id]

                # don't link twice the same tag to the same parent
                if dup_tag_id != tag_id:
                    dup_tag = tag_dic[dup_tag_id]
                    target_object.linkAnnotation(dup_tag)


def move_to_group(conn, target, target_ids, current_group, target_group, target_group_name):
    import_args = ["chgrp",
                   "Group:%s" % target_group.getId(),
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
            cli.get_client().closeSession()
            cli.close()
            return message, err
        cli.get_client().closeSession()

    return f"Successful transfer of {target} : '{target_ids}' to group '{target_group_name}'", None


def run_script():
    # Cannot add fancy layout if we want auto fill and select of object ID
    source_types = [
        rstring(PROJECT_CLASS), rstring(DATASET_CLASS), rstring(IMAGE_CLASS),
        rstring(SCREEN_CLASS), rstring(PLATE_CLASS)
    ]

    client = scripts.client(
        'Share images across groups',
        """
    This script duplicates selected images in the specified dataset and transfer the dataset from the current group to the specified target group.
        """,
        scripts.String(
            P_DATA_TYPE, optional=False,grouping="1",
            description="Choose source of images (only Images supported)",
            values=source_types, default="Image"),

        scripts.List(
            P_IDS,  optional=False, grouping="2",
            description="List of Images IDs to link to another"
                        " group.").ofType(rlong(0)),
        scripts.String(
            P_GROUP, optional=False, grouping="3",
            description="Target group where to copy images"),

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
        message, err = duplicate_and_move_to_group(conn, script_params)
        client.setOutput("Message", rstring(message))
        # if res_obj is not None:
        #     client.setOutput("Result", robject(res_obj._obj))
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
