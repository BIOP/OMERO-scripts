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


Warning: the user who is running the script must be a group owner


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
P_PROJECT = "Target project"
P_DATASET = "Dataset"

CONTAINER_OBJECTS = ["Project", "Dataset", "Image", "Screen", "Plate", "PlateAcquisition", "Well"]
IMAGE_LINKED_OBJECTS = ["Fileset", "Instrument", "Roi", "Channel"]
INSTRUMENT_LINKED_OBJECTS = ["Objective",  "Filter", "Dichroic", "Detector"]
ROI_LINKED_OBJECTS = ["Shape"]

NOT_HANDLED_OBJECTS = ["Session", "Namespace", "Node", "LightPath", "Job", "OriginalFile", "Experimenter",
                       "ExperimenterGroup", "Reagent", "Annotation", "LightSource", "FolderImage, FolderRoi"]

parentObject = {
    'Project': omero.gateway.ProjectWrapper,
    'Dataset': omero.gateway.DatasetWrapper,
    'Image': omero.gateway.ImageWrapper,
    'Screen': omero.gateway.ScreenWrapper,
    'Plate': omero.gateway.PlateWrapper,
    'PlateAcquisition': omero.gateway.PlateAcquisitionWrapper,
    'Well': omero.gateway.WellWrapper,
    'Fileset': omero.gateway.FilesetWrapper,
    'Instrument': omero.gateway.InstrumentWrapper,
    'Roi': omero.gateway.RoiWrapper,
    'Channel': omero.gateway.ChannelWrapper,
    'Objective': omero.gateway.ObjectiveWrapper,
    'Filter': omero.gateway.FilterWrapper,
    'Dichroic': omero.gateway.DichroicWrapper,
    'Detector': omero.gateway.DetectorWrapper,
    'Shape': omero.gateway.ShapeWrapper
}


OMERO_SERVER = "omero-server-poc.epfl.ch"
OMERO_WEBSERVER = "omero-poc.epfl.ch"
PORT = "4064"



def list_tag_attached(conn, qs, params, target, ids, src_group_tags):
    objects_tagged_map = {}
    tag_target_count_dict = {}
    objects_map = {}
    tag_annotation_link_dic = {}
    # get all the container tags => most important

    image_ids = ids

    target = "Image"
    roi_service = conn.getRoiService()

    instrument_ids = []
    fileset_ids = []
    channel_ids = []
    detector_ids = []
    dichroic_ids = []
    filter_ids = []
    obj_ids = []
    roi_ids = []
    shape_ids = []
    tag_set = set()

    available_tags_src_group_ids = src_group_tags.keys()
    available_tags_src_group_ids = [str(img_id) for img_id in available_tags_src_group_ids]

    # get the tags attached to the different entities
    for image_id in image_ids:
        image_obj = conn.getObject("Image", image_id)
        instrument_obj = image_obj.getInstrument()

        if instrument_obj is not None:
            instrument_ids.append(str(instrument_obj.getId()))
            detector_ids = detector_ids + [str(det.getId()) for det in instrument_obj.getDetectors() if det is not None]
            dichroic_ids = dichroic_ids + [str(dic.getId()) for dic in instrument_obj.getDichroics() if dic is not None]
            filter_ids = filter_ids + [str(flt.getId()) for flt in instrument_obj.getFilters() if flt is not None]
            obj_ids = obj_ids + [str(obj.getId()) for obj in instrument_obj.getObjectives() if obj is not None]

        if image_obj is not None:
            fileset_ids.append(str(image_obj.getFileset().getId()))
            channel_ids = channel_ids + [str(ch.getId()) for ch in image_obj.getChannels()]

        # get roi & shape tags
        result = roi_service.findByImage(image_id, None)
        for roi in result.rois:
            roi_ids.append(str(roi.getId().getValue()))
            shape_ids = shape_ids + [str(s.getId().getValue()) for s in roi.copyShapes()]

    print(f"Getting tags & object links for {len(set(image_ids))} images")
    image_ids = [str(img_id) for img_id in image_ids]
    tags_on_images, image_objects_ids, unique_image_tags, image_annotation_link_dic = get_tag_attached(conn, qs, params, "Image", set(image_ids), available_tags_src_group_ids)
    objects_tagged_map = {**objects_tagged_map, **tags_on_images}
    objects_map["Image"] = image_objects_ids
    tag_annotation_link_dic = {**tag_annotation_link_dic, **image_annotation_link_dic}
    tag_set = tag_set.union(unique_image_tags)
    print(image_annotation_link_dic)

    print(f"Getting tags & object links for {len(set(instrument_ids))} instruments")
    tags_on_inst, inst_objects_ids, unique_inst_tags, inst_annotation_link_dic = get_tag_attached(conn, qs, params, "Instrument", set(instrument_ids), available_tags_src_group_ids)
    objects_tagged_map = {**objects_tagged_map, **tags_on_inst}
    objects_map["Instrument"] = inst_objects_ids
    tag_annotation_link_dic = {**tag_annotation_link_dic, **inst_annotation_link_dic}
    tag_set = tag_set.union(unique_inst_tags)

    print(f"Getting tags & object links for {len(set(fileset_ids))} filesets")
    tags_on_fs, fs_objects_ids, unique_fs_tags, fs_annotation_link_dic = get_tag_attached(conn, qs, params,"Fileset", set(fileset_ids), available_tags_src_group_ids)
    objects_tagged_map = {**objects_tagged_map, **tags_on_fs}
    objects_map["Fileset"] = fs_objects_ids
    tag_annotation_link_dic = {**tag_annotation_link_dic, **fs_annotation_link_dic}
    tag_set = tag_set.union(unique_fs_tags)

    print(f"Getting tags & object links for {len(set(channel_ids))} channels")
    tags_on_ch, ch_objects_ids, unique_ch_tags, ch_annotation_link_dic = get_tag_attached(conn, qs, params, "Channel", set(channel_ids), available_tags_src_group_ids)
    objects_tagged_map = {**objects_tagged_map, **tags_on_ch}
    objects_map["Channel"] = ch_objects_ids
    tag_annotation_link_dic = {**tag_annotation_link_dic, **ch_annotation_link_dic}
    tag_set = tag_set.union(unique_ch_tags)

    print(f"Getting tags & object links for {len(set(detector_ids))} detectors")
    tags_on_det, det_objects_ids, unique_det_tags, det_annotation_link_dic = get_tag_attached(conn, qs, params,"Detector", set(detector_ids), available_tags_src_group_ids)
    objects_tagged_map = {**objects_tagged_map, **tags_on_det}
    objects_map["Detector"] = det_objects_ids
    tag_annotation_link_dic = {**tag_annotation_link_dic, **det_annotation_link_dic}
    tag_set = tag_set.union(unique_det_tags)

    print(f"Getting tags & object links for {len(set(dichroic_ids))} dichroics")
    tags_on_dic, dic_objects_ids, unique_dic_tags, dic_annotation_link_dic = get_tag_attached(conn, qs, params,"Dichroic", set(dichroic_ids), available_tags_src_group_ids)
    objects_tagged_map = {**objects_tagged_map, **tags_on_dic}
    objects_map["Dichroic"] = dic_objects_ids
    tag_annotation_link_dic = {**tag_annotation_link_dic, **dic_annotation_link_dic}
    tag_set = tag_set.union(unique_dic_tags)

    print(f"Getting tags & object links for {len(set(filter_ids))} filters")
    tags_on_flt, flt_objects_ids, unique_flt_tags, flt_annotation_link_dic = get_tag_attached(conn, qs, params,"Filter", set(filter_ids), available_tags_src_group_ids)
    objects_tagged_map = {**objects_tagged_map, **tags_on_flt}
    objects_map["Filter"] = flt_objects_ids
    tag_annotation_link_dic = {**tag_annotation_link_dic, **flt_annotation_link_dic}
    tag_set = tag_set.union(unique_flt_tags)

    print(f"Getting tags & object links for {len(set(obj_ids))} objectives")
    tags_on_obj, obj_objects_ids, unique_obj_tags, obj_annotation_link_dic = get_tag_attached(conn, qs, params,"Objective", set(obj_ids), available_tags_src_group_ids)
    objects_tagged_map = {**objects_tagged_map, **tags_on_obj}
    objects_map["Objective"] = obj_objects_ids
    tag_annotation_link_dic = {**tag_annotation_link_dic, **obj_annotation_link_dic}
    tag_set = tag_set.union(unique_obj_tags)

    print(f"Getting tags & object links for {len(set(roi_ids))} rois")
    tags_on_rois, roi_objects_ids, unique_rois_tags, roi_annotation_link_dic = get_tag_attached(conn,  qs, params,"Roi", set(roi_ids), available_tags_src_group_ids)
    objects_tagged_map = {**objects_tagged_map, **tags_on_rois}
    objects_map["Roi"] = roi_objects_ids
    tag_annotation_link_dic = {**tag_annotation_link_dic, **roi_annotation_link_dic}
    tag_set = tag_set.union(unique_rois_tags)

    print(f"Getting tags & object links for {len(set(shape_ids))} shapes")
    tags_on_shp, shp_objects_ids, unique_shp_tags, shp_annotation_link_dic = get_tag_attached(conn,  qs, params,"Shape", set(shape_ids), available_tags_src_group_ids)
    objects_tagged_map = {**objects_tagged_map, **tags_on_shp}
    objects_map["Shape"] = shp_objects_ids
    tag_annotation_link_dic = {**tag_annotation_link_dic, **shp_annotation_link_dic}
    tag_set = tag_set.union(unique_shp_tags)

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
    target_ids = script_params[P_IDS]

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

    # get all tags linked to objects
    object_tag_dic, object_dic, tag_list, tag_objects_count_dic, tag_annotation_link_dic = list_tag_attached(conn, qs, params, script_params[P_DATA_TYPE], target_ids, src_group_tags)

    # remove current tag links to current objects
    for obj_type, ann_link_dic in tag_annotation_link_dic.items():
        conn.deleteObjects(f"{obj_type}AnnotationLink", ann_link_dic, wait=True)

    # move to the target group
    target_ids = [str(img_id) for img_id in target_ids]
    move_to_group(conn, script_params[P_DATA_TYPE], target_ids, current_group, target_group, target_group_name)

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
                   f"Image:{','.join(target_ids)}",
                   "--include", "Image", "Annotation"
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
