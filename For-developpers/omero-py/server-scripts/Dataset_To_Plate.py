#!/usr/bin/env python
# -*- coding: utf-8 -*-
# -----------------------------------------------------------------------------
#   Copyright (C) 2006-2021 University of Dundee. All rights reserved.
#
#
#   This program is free software; you can redistribute it and/or modify
#   it under the terms of the GNU General Public License as published by
#   the Free Software Foundation; either version 2 of the License, or
#   (at your option) any later version.
#   This program is distributed in the hope that it will be useful,
#   but WITHOUT ANY WARRANTY; without even the implied warranty of
#   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#   GNU General Public License for more details.
#
#   You should have received a copy of the GNU General Public License along
#   with this program; if not, write to the Free Software Foundation, Inc.,
#   51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
#
# ------------------------------------------------------------------------------

"""
This script converts a Dataset of Images to a Plate, with one image per Well.
"""

# @author Will Moore
# <a href="mailto:will@lifesci.dundee.ac.uk">will@lifesci.dundee.ac.uk</a>
# @version 4.3
# @since 3.0-Beta4.3

import omero.scripts as scripts
from omero.gateway import BlitzGateway, PlateWrapper
import omero
import re

from omero.rtypes import rint, rlong, rstring, robject, unwrap

P_DATA_TYPE = "Data_Type"
P_IDS = "IDs"
P_FILTER_NAMES = "Filter_Names"
P_READ_FROM_IMAGE = "Read_Well_From_Image_Name"
P_PATTERN = "Pattern"
P_FIRST_AXIS = "First_Axis"
P_FIRST_AXIS_COUNT = "First_Axis_Count"
P_IMAGE_PER_WELL = "Images_Per_Well"
P_COLUMN_NAME = "Column_Names"
P_ROW_NAME = "Row_Names"
P_SCREEN = "Screen"
P_REMOVE_FROM_DATASET = "Remove_From_Dataset"

DEFAULT_PATTERN = "<select>"
EVOS_TEMPLATE = "EVOS <..._Plate_<RunId>_p00_0_<wellA00>f00d0.tif>"


position_template_map = {
    EVOS_TEMPLATE : r".*_Plate_(?P<run>\w*)_p\d*_\d*_(?P<wellRow>\w)(?P<wellColumn>\d*)f(?P<field>\d*)d\d*.(TIF|tif|TIFF|tiff)"}


def add_images_to_plate(conn, run_map, created_runs_map, plate_id, column, row, remove_from=None):
    """
    Add the Images to a Plate, creating a new well at the specified column and
    row
    NB - This will fail if there is already a well at that point
    """
    update_service = conn.getUpdateService()
    run_images = []

    well = omero.model.WellI()
    well.plate = omero.model.PlateI(plate_id, False)
    well.column = rint(column)
    well.row = rint(row)

    try:
        # create wells with multiple images
        for run_key in run_map.keys():
            if created_runs_map.get(run_key) is None:
                # create plate acquisition
                plate_acquisition = omero.model.PlateAcquisitionI()
                plate_acquisition.name = omero.rtypes.RStringI(run_key)
                plate_acquisition.plate = omero.model.PlateI(plate_id, False)
                plate_acquisition = update_service.saveAndReturnObject(plate_acquisition)
                created_runs_map[run_key] = plate_acquisition
            run_images = run_map[run_key]
            run = created_runs_map[run_key]

            for image in run_images:
                ws = omero.model.WellSampleI()
                ws.image = omero.model.ImageI(image.id, False)
                ws.well = well
                ws.plateAcquisition = omero.model.PlateAcquisitionI(run.id, False)
                well.addWellSample(ws)
        update_service.saveObject(well)
    except Exception:
        return False

    # remove from Dataset
    for image in run_images:
        if remove_from is not None:
            links = list(image.getParentLinks(remove_from.id))
            link_ids = [link.id for link in links]
            conn.deleteObjects('DatasetImageLink', link_ids)
    return True


def dataset_to_plate(conn, script_params, dataset_id, screen):
    dataset = conn.getObject("Dataset", dataset_id)
    if dataset is None:
        return

    update_service = conn.getUpdateService()

    # create Plate
    plate = omero.model.PlateI()
    plate.name = omero.rtypes.RStringI(dataset.name)
    plate.columnNamingConvention = rstring(str(script_params[P_COLUMN_NAME]))
    # 'letter' or 'number'
    plate.rowNamingConvention = rstring(str(script_params[P_ROW_NAME]))
    plate = update_service.saveAndReturnObject(plate)

    if screen is not None and screen.canLink():
        link = omero.model.ScreenPlateLinkI()
        link.parent = omero.model.ScreenI(screen.id, False)
        link.child = omero.model.PlateI(plate.id.val, False)
        update_service.saveObject(link)
    else:
        link = None

    # sort images by name
    images = list(dataset.listChildren())
    dataset_img_count = len(images)
    if P_FILTER_NAMES in script_params:
        filter_by = script_params[P_FILTER_NAMES]
        images = [i for i in images if i.getName().find(filter_by) >= 0]
    images.sort(key=lambda x: x.name.lower())

    # Do we try to remove images from Dataset and Delete Dataset when/if empty?
    remove_from = None
    remove_dataset = P_REMOVE_FROM_DATASET in script_params and \
                     script_params[P_REMOVE_FROM_DATASET]
    if remove_dataset:
        remove_from = dataset

    images_per_well = script_params[P_IMAGE_PER_WELL]

    # sort images in the right well, either by default layout or by reading image name
    well_map = sort_by_well(images, images_per_well, script_params)
    created_runs_map = {}

    for well_key in well_map.keys():
        position = well_key.split("_")
        row = position[0]
        col = position[1]
        run_map = well_map.get(well_key)
        added_count = add_images_to_plate(conn, run_map, created_runs_map,
                                          plate.getId().getValue(),
                                          col, row, remove_from)
    for run in created_runs_map.keys():
        plate.addPlateAcquisition(created_runs_map[run])
    update_service.saveObject(omero.model.PlateI(plate.id, False))

    # if user wanted to delete dataset, AND it's empty we can delete dataset
    delete_dataset = False  # Turning this functionality off for now.
    delete_handle = None
    if delete_dataset:
        if dataset_img_count == added_count:
            dcs = list()
            options = None  # {'/Image': 'KEEP'}    # don't delete the images!
            dcs.append(omero.api.delete.DeleteCommand(
                "/Dataset", dataset.id, options))
            delete_handle = conn.getDeleteService().queueDelete(dcs)
    return plate, link, delete_handle


def sort_by_well(images, images_per_well, script_params):
    well_map = {}
    read_from_image = script_params[P_READ_FROM_IMAGE]
    pattern_name = script_params[P_PATTERN]
    # get the image pattern
    pattern = position_template_map.get(pattern_name)

    if read_from_image and pattern_name != DEFAULT_PATTERN:
        pattern = re.compile(pattern)
        for image in images:
            for match in pattern.finditer(image.name):
                # get the well position in the plate
                well_row = match.group("wellRow")
                well_column = match.group("wellColumn")
                run = match.group("run")
                well = f"{convert_letter_to_number(well_row)}_{int(well_column)-1}"
                if well_map.get(well) is not None:
                    plt_acq_map = well_map.get(well)
                    if plt_acq_map.get(run) is not None:
                        image_list = plt_acq_map.get(run)
                    else:
                        image_list = []
                        plt_acq_map[run] = []
                else:
                    image_list = []
                    plt_acq_map = {run: image_list}
                    well_map[well] = plt_acq_map
                image_list.append(image)
                well_map[well][run] = image_list
    else:
        image_index = 0
        row = 0
        col = 0

        first_axis_is_row = script_params[P_FIRST_AXIS] == 'row'
        axis_count = script_params[P_FIRST_AXIS_COUNT]

        plt_acq_map = {}
        while image_index < len(images):
            plt_acq_map["Run0"] = images[image_index: image_index + images_per_well]
            image_index += images_per_well

            # update row and column index
            if first_axis_is_row:
                row += 1
                if row >= axis_count:
                    row = 0
                    col += 1
            else:
                col += 1
                if col >= axis_count:
                    col = 0
                    row += 1

            well_map[f"{row}_{col}"] = plt_acq_map
    return well_map


def convert_letter_to_number(letter):
    return ord(letter.upper()) - 65


def datasets_to_plates(conn, script_params):
    update_service = conn.getUpdateService()

    message = ""

    # Get the datasets ID
    dtype = script_params[P_DATA_TYPE]
    ids = script_params[P_IDS]
    datasets = list(conn.getObjects(dtype, ids))

    def has_images_linked_to_well(dataset):
        params = omero.sys.ParametersI()
        query = "select count(well) from Well as well " \
                "left outer join well.wellSamples as ws " \
                "left outer join ws.image as img " \
                "where img.id in (:ids)"
        params.addIds([i.getId() for i in dataset.listChildren()])
        n_wells = unwrap(conn.getQueryService().projection(
            query, params, conn.SERVICE_OPTS)[0])[0]
        if n_wells > 0:
            return True
        else:
            return False

    # Exclude datasets containing images already linked to a well
    n_datasets = len(datasets)
    datasets = [x for x in datasets if not has_images_linked_to_well(x)]
    if len(datasets) < n_datasets:
        message += "Excluded %s out of %s dataset(s). " \
                   % (n_datasets - len(datasets), n_datasets)

    # Return if all input dataset are not found or excluded
    if not datasets:
        return None, message

    # Filter dataset IDs by permissions
    ids = [ds.getId() for ds in datasets if ds.canLink()]
    if len(ids) != len(datasets):
        perm_ids = [str(ds.getId()) for ds in datasets if not ds.canLink()]
        message += "You do not have the permissions to add the images from" \
                   " the dataset(s): %s." % ",".join(perm_ids)
    if not ids:
        return None, message

    # find or create Screen if specified
    screen = None
    newscreen = None
    if P_SCREEN in script_params and len(script_params[P_SCREEN]) > 0:
        s = script_params[P_SCREEN]
        # see if this is ID of existing screen
        try:
            screen_id = int(s)
            screen = conn.getObject("Screen", screen_id)
        except ValueError:
            pass
        # if not, create one
        if screen is None:
            newscreen = omero.model.ScreenI()
            newscreen.name = rstring(s)
            newscreen = update_service.saveAndReturnObject(newscreen)
            screen = conn.getObject("Screen", newscreen.getId().getValue())

    plates = []
    links = []
    deletes = []
    for dataset_id in ids:
        plate, link, delete_handle = dataset_to_plate(conn, script_params,
                                                      dataset_id, screen)
        if plate is not None:
            plates.append(plate)
        if link is not None:
            links.append(link)
        if delete_handle is not None:
            deletes.append(delete_handle)

    # wait for any deletes to finish
    for handle in deletes:
        cb = omero.callbacks.DeleteCallbackI(conn.c, handle)
        while True:  # ms
            if cb.block(100) is not None:
                break

    if newscreen:
        message += "New screen created: %s." % newscreen.getName().getValue()
        robj = newscreen
    elif plates:
        robj = plates[0]
    else:
        robj = None

    if plates:
        if len(plates) == 1:
            plate = plates[0]
            message += " New plate created: %s" % plate.getName().getValue()
        else:
            message += " %s plates created" % len(plates)
        if len(plates) == len(links):
            message += "."
        else:
            message += " but could not be attached."
    else:
        message += "No plate created."
    return robj, message


def run_script():
    """
    The main entry point of the script, as called by the client via the
    scripting service, passing the required parameters.
    """

    data_types = [rstring('Dataset')]
    first_axis = [rstring('column'), rstring('row')]
    row_col_naming = [rstring('letter'), rstring('number')]
    patterns = [rstring(EVOS_TEMPLATE), rstring(DEFAULT_PATTERN)]

    client = scripts.client(
        'Dataset_To_Plate.py',
        """Take a Dataset of Images and put them in a new Plate, \
arranging them into rows or columns as desired.
Optionally add the Plate to a new or existing Screen.
See http://help.openmicroscopy.org/scripts.html""",

        scripts.String(
            P_DATA_TYPE, optional=False, grouping="1",
            description="Choose source of images (only Dataset supported)",
            values=data_types, default="Dataset"),

        scripts.List(
            P_IDS, optional=False, grouping="2",
            description="List of Dataset IDs to convert to new"
                        " Plates.").ofType(rlong(0)),

        scripts.String(
            P_FILTER_NAMES, grouping="2.1",
            description="Filter the images by names that contain this value"),

        scripts.Bool(
            P_READ_FROM_IMAGE, grouping="3", default=True,
            description="""Read the well position A00 in the image name"
            " according the chosen pattern"""),

        scripts.String(
            P_PATTERN, optional=False, grouping="3.1",
            description="Choose the image name pattern",
            values=patterns, default=DEFAULT_PATTERN),

        scripts.String(
            P_FIRST_AXIS, grouping="4", optional=False, default='column',
            values=first_axis,
            description="""Arrange images across 'column' first or down"
            " 'row'"""),

        scripts.Int(
            P_FIRST_AXIS_COUNT, grouping="4.1", optional=False, default=12,
            description="Number of Rows or Columns in the 'First Axis'",
            min=1),

        scripts.Int(
            P_IMAGE_PER_WELL, grouping="4.2", optional=False, default=1,
            description="Number of Images (Well Samples) per Well",
            min=1),

        scripts.String(
            P_COLUMN_NAME, grouping="4.3", optional=False, default='number',
            values=row_col_naming,
            description="""Name plate columns with 'number' or 'letter'"""),

        scripts.String(
            P_ROW_NAME, grouping="4.4", optional=False, default='letter',
            values=row_col_naming,
            description="""Name plate rows with 'number' or 'letter'"""),

        scripts.String(
            P_SCREEN, grouping="5",
            description="Option: put Plate(s) in a Screen. Enter Name of new"
                        " screen or ID of existing screen"""),

        scripts.Bool(
            P_REMOVE_FROM_DATASET, grouping="6", default=True,
            description="Remove Images from Dataset as they are added to"
                        " Plate"),

        version="4.3.2",
        authors=["William Moore", "OME Team"],
        institutions=["University of Dundee"],
        contact="ome-users@lists.openmicroscopy.org.uk",
    )

    try:
        script_params = client.getInputs(unwrap=True)

        # wrap client to use the Blitz Gateway
        conn = BlitzGateway(client_obj=client)

        # convert Dataset(s) to Plate(s). Returns new plates or screen
        new_obj, message = datasets_to_plates(conn, script_params)

        client.setOutput("Message", rstring(message))
        if new_obj:
            client.setOutput("New_Object", robject(new_obj))

    finally:
        client.closeSession()


if __name__ == "__main__":
    run_script()
