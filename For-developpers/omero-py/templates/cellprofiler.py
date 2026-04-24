#!/usr/bin/env python
# -*- coding: utf-8 -*-
#
#
# Copyright (c) 2020 University of Dundee.
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as
# published by the Free Software Foundation, either version 3 of the
# License, or (at your option) any later version.
# FPBioimage was originally published in
# <https://www.nature.com/nphoton/journal/v11/n2/full/nphoton.2016.273.html>.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program. If not, see <http://www.gnu.org/licenses/>.
#
# Version: 1.0
#
import os
import warnings
import traceback

import omero
from omero.gateway import BlitzGateway
from omero.gateway import ImageWrapper, DatasetWrapper
from omero.rtypes import rstring, rint

import skimage as ski
from skimage.measure import find_contours
from cellprofiler_core.pipeline import Pipeline
from cellprofiler_core.modules.injectimage import InjectImage
import cellprofiler_core.preferences as cpprefs

from PyQt6.QtWidgets import QLineEdit, QLabel, QPushButton, QMainWindow, QVBoxLayout, \
    QWidget, QApplication, QHBoxLayout, QSpinBox, QCheckBox


# module names
MOD_EXPORT_TO_SPREADSHEET = "ExportToSpreadsheet"
MOD_CONVERT_OBJECTS_TO_IMAGE = "ConvertObjectsToImage"
MOD_SAVE_IMAGES = "SaveImages"
MOD_NAMES_AND_TYPES = "NamesAndTypes"

# ConvertObjectsToImage module attributes name
COTI_OBJECT_TO_EXPORT = "Select the input objects"
COTI_OUTPUT_IMAGE_NAME = "Name the output image"

# SaveImages module attributes name
SI_IMAGE_SAVE = "Select the image to save"
SI_FILE_NAME = "Enter single file name"
SI_FILE_FORMAT = "Saved file format"

# NamesAndTypes module attributes name
NAT_IMAGE_NAME = "Name to assign these images"
NAT_SELECTION_CRITERIA = "Select the rule criteria"

# UI default settings
FONT_SIZE = 'font-size: 14px'
DEFAULT_HOST = 'omero-server.epfl.ch'

#TODO ADD those parameters to the UI
dataset_id = 2715
new_output_directory = "/home/biop/Desktop/Output"
pipeline_path = "/home/biop/ExampleHuman/ExampleHuman5.cppipe"
is_sending_rois = True


class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()

        # main window settings
        self.setWindowTitle("Main window title")
        self.setMinimumSize(400, 100)
        widgets = []
        main_layout = QVBoxLayout()

        # host fields
        host_layout = QHBoxLayout()
        host_label = QLabel("Host")
        host_label.setStyleSheet(FONT_SIZE)
        self.host = QLineEdit()
        self.host.setStyleSheet(FONT_SIZE)
        self.host.setText(DEFAULT_HOST)
        host_widget = QWidget()
        host_layout.addWidget(host_label)
        host_layout.addWidget(self.host)
        host_widget.setLayout(host_layout)
        widgets.append(host_widget)

        # username fields
        username_layout = QHBoxLayout()
        username_label = QLabel("Username")
        username_label.setStyleSheet(FONT_SIZE)
        self.username = QLineEdit()
        self.username.setStyleSheet(FONT_SIZE)
        username_widget = QWidget()
        username_layout.addWidget(username_label)
        username_layout.addWidget(self.username)
        username_widget.setLayout(username_layout)
        widgets.append(username_widget)

        # password fields
        password_layout = QHBoxLayout()
        password_label = QLabel("Password")
        password_label.setStyleSheet(FONT_SIZE)
        self.password = QLineEdit()
        self.password.setStyleSheet(FONT_SIZE)
        self.password.setEchoMode(QLineEdit.EchoMode.Password)
        password_widget = QWidget()
        password_layout.addWidget(password_label)
        password_layout.addWidget(self.password)
        password_widget.setLayout(password_layout)
        widgets.append(password_widget)

        # buttons fields
        button_layout = QHBoxLayout()
        ok_button = QPushButton(text="OK")
        ok_button.setStyleSheet(FONT_SIZE)
        ok_button.clicked.connect(self.run_app)
        cancel_button = QPushButton(text="Cancel")
        cancel_button.setStyleSheet(FONT_SIZE)
        cancel_button.clicked.connect(self.close_app)
        button_widget = QWidget()
        button_layout.addWidget(ok_button)
        button_layout.addWidget(cancel_button)
        button_widget.setLayout(button_layout)
        widgets.append(button_widget)

        # building the main GUI
        for w in widgets:
            main_layout.addWidget(w)

        widget = QWidget()
        widget.setLayout(main_layout)

        # Set the central widget of the Window. Widget will expand
        # to take up all the space in the window by default.
        self.setCentralWidget(widget)

    def close_app(self):
        self.close()

    def run_app(self):
        username = self.username.text()
        password = self.password.text()
        host = self.host.text()
        self.close()
        run_script(host, username, password)


def run_script(host, username, password):
    conn = BlitzGateway(username, password, host=host, port=4064, secure=True)
    conn.connect()

    if conn.isConnected():
        print(f"Connected to {host}")
        try:
            # Configure cell Profiler
            configure_cellprofiler(new_output_directory)

            # Read the pipeline
            pipeline, image_names = load_pipeline(pipeline_path)

            # Load the dataset
            dataset = load_dataset(conn, dataset_id)

            # Run CellProfiler
            print("Running CellProfiler pipeline...")
            analyze(conn, dataset, pipeline, is_sending_rois, image_names)
            print("End of the script")
        except Exception as e:
            print(e)
            traceback.print_exc()
        finally:
            conn.close()
            print(f"Disconnected from {host}")



def load_dataset(conn, dataset_id):
    """
    Load OMERO dataset object

    Parameters
    ----------
    conn : BlitzGateway
        Gateway that handles the OMERO connection

    dataset_id : long
        The id of the dataset to retrieve from OMERO

    Returns
    -------
    DatasetWrapper
        The dataset OMERO object
    """
    return conn.getObject("Dataset", dataset_id)


def load_pipeline(pipeline_path):
    """
    Load CellProfiler pipeline and remove the first 4 modules. Those modules are dealing with the file location,
    which we don't care here as images are on OMERO. OMERO images are handled later, using the InjectImage module.

    Parameters
    ----------
    pipeline_path : str
        Absolute path to the Cellprofiler pipeline file

    Returns
    -------
    Pipeline
        CellProfiler Pipeline object

    list[str]
         Name of the images as it appears in the cellprofiler pipeline
    """
    pipeline = Pipeline()
    pipeline.load(pipeline_path)
    image_names = get_channel_names(pipeline)
    # Remove first 4 modules: Images, Metadata, NamesAndTypes, Groups...
    # (replaced by InjectImage module below)
    for i in range(4):
        print('Remove module: ', pipeline.modules()[0].module_name)
        pipeline.remove_module(1)
    print('Pipeline modules:')
    for module in pipeline.modules():
        for s in module.settings():
            print(str(s.text)) # useful to get the name given by the user to the output file of a certain module
            print(str(s.value))
        print(module.module_name)

    return pipeline, image_names


def configure_cellprofiler(output_dir):
    """
    Set CellProfiler to be used correctly with the API and define the default output directory

    Parameters
    ----------
    output_dir : str
        Absolute path to the output directory where results will be saved.

    Returns
    -------

    """
    cpprefs.set_headless()
    warnings.filterwarnings('ignore')

    # Set Cell Output Directory
    cpprefs.set_default_output_directory(output_dir)



def analyze(conn, dataset, pipeline, is_sending_rois, image_names):
    """
    Extract cellprofiler attributes for saving results and objects,
    run cellprofiler pipeline and save results / objects if specified in the pipeline

    Parameters
    ----------
    conn : BlitzGateway
        Gateway that handles the OMERO connection

    dataset : DatasetWrapper
        The current OMERO dataset object

    pipeline : Pipeline
        The current pipeline object

    is_sending_rois : bool
        True to save mask images as ROI objects

    image_names : list[str]
        Name of the images as it appears in the cellprofiler pipeline

    Returns
    -------

    """
    files = list()
    images = list(dataset.listChildren())
    saving_modules = get_saving_modules(pipeline, is_sending_rois)

    for count, image in enumerate(images):
        print(image.getName())
        pixels = image.getPrimaryPixels()
        size_c = image.getSizeC()
        # For each Image in OMERO, we copy pipeline and inject image modules
        pipeline_copy = pipeline.copy()
        # Inject image for each Channel (pipeline only handles 2 channels)
        for c in range(0, min(size_c, len(image_names))):
            plane = pixels.getPlane(0, c, 0)
            image_name = image_names[c]
            inject_image_module = InjectImage(image_name, plane)
            inject_image_module.set_module_num(1)
            pipeline_copy.add_module(inject_image_module)
        pipeline_copy.run()

        if MOD_EXPORT_TO_SPREADSHEET in saving_modules.keys():
            print("saving results...")
            save_results(conn, image)
        if MOD_CONVERT_OBJECTS_TO_IMAGE in saving_modules.keys():
            print("saving rois...")
            save_rois(conn, image, saving_modules[MOD_CONVERT_OBJECTS_TO_IMAGE], saving_modules[MOD_SAVE_IMAGES])

    return files


def get_channel_names(pipeline):
    """
    Extract channel names from the cellprofiler pipeline

    Parameters
    ----------
    pipeline : Pipeline
        The current pipeline object

    Returns
    -------
    list[str]
        Name of the images as it appears in the cellprofiler pipeline
    """
    image_names = []
    # loop over the pipeline modules
    for module in pipeline.modules():
        if module.module_name.lower() == MOD_NAMES_AND_TYPES.lower():
            prev_setting = None
            for s in module.settings():
                # get the image name
                if (s.text.lower() == NAT_IMAGE_NAME.lower() and prev_setting is not None and
                        prev_setting.text.lower() == NAT_SELECTION_CRITERIA.lower()):
                    image_names.append(s.value)
                prev_setting = s
    return image_names


def get_saving_modules(pipeline, is_sending_rois):
    """
    Extract from the pipeline the different module settings that are necessary for saving results on OMERO

    Saving results as spreadsheet
        * Needs the module ExportToSpreadsheet to save csv or tsv files
    Saving objects as ROIs
        * Needs the module ConvertObjectsToImage to create a mask image of the objects
        * Needs the module SaveImage to save the mask image

    Parameters
    ----------
    pipeline : Pipeline
        The current pipeline object

    is_sending_rois : bool
        True to save mask images as ROI objects

    Returns
    -------
    dict
        Dictionary of the interesting module settings
    """
    saving_modules = {}

    # loop over the pipeline modules
    for module in pipeline.modules():
        # if saving tables of results
        if module.module_name.lower() == MOD_EXPORT_TO_SPREADSHEET.lower():
            saving_modules[MOD_EXPORT_TO_SPREADSHEET] = True

        # if saving mask images, convert mask into ROIs objects
        # This requires to have both modules MOD_CONVERT_OBJECTS_TO_IMAGE and MOD_SAVE_IMAGES in the pipeline
        if is_sending_rois:

            # extract information about which object to export from which image
            if module.module_name.lower() == MOD_CONVERT_OBJECTS_TO_IMAGE.lower():
                settings_attributes = {}
                for s in module.settings():
                    # get the label image name
                    if s.text.lower() == COTI_OUTPUT_IMAGE_NAME.lower():
                        settings_attributes[COTI_OUTPUT_IMAGE_NAME] = s.value

                    # get the object type to export
                    if s.text.lower() == COTI_OBJECT_TO_EXPORT.lower():
                        settings_attributes[COTI_OBJECT_TO_EXPORT] = s.value

                if MOD_CONVERT_OBJECTS_TO_IMAGE in saving_modules.keys():
                    saving_modules[MOD_CONVERT_OBJECTS_TO_IMAGE].append(settings_attributes)
                else:
                    saving_modules[MOD_CONVERT_OBJECTS_TO_IMAGE] = [settings_attributes]

            # extract information about where the mask is saved and under which name and format
            if module.module_name.lower() == MOD_SAVE_IMAGES.lower():
                settings_attributes = {}
                for s in module.settings():
                    # get the label image name
                    if s.text.lower() == SI_IMAGE_SAVE.lower():
                        settings_attributes[SI_IMAGE_SAVE] = s.value

                    # get the object type to export
                    if s.text.lower() == SI_FILE_NAME.lower():
                        settings_attributes[SI_FILE_NAME] = s.value

                    # get the image format
                    if s.text.lower() == SI_FILE_FORMAT.lower():
                        settings_attributes[SI_FILE_FORMAT] = s.value

                if MOD_SAVE_IMAGES in saving_modules.keys():
                    saving_modules[MOD_SAVE_IMAGES].append(settings_attributes)
                else:
                    saving_modules[MOD_SAVE_IMAGES] = [settings_attributes]

    return saving_modules


def save_results(conn, image):
    """
    Send the tables of results from cellprofiler pipeline to OMERO as attachment to the current image

    Parameters
    ----------
    conn : BlitzGateway
        Gateway that handles the OMERO connection

    image : ImageWrapper
        The current OMERO image object
    """
    namespace = "cellprofiler.demo.namespace"
    files = []

    # get the files from the output dir
    for file in os.listdir(cpprefs.get_default_output_directory()):
        if file.endswith(".csv") or file.endswith(".tsv"):
            files.append(os.path.join(cpprefs.get_default_output_directory(), file))

    # Upload the CSV files
    for f in files:
        ann = conn.createFileAnnfromLocalFile(f, mimetype="text/csv", ns=namespace, desc=None)
        image.linkAnnotation(ann)


def save_rois(conn, image, label_image_list, save_label_list):
    """
    Segment the mask images, extract ROI objects and send them to OMERO, attached to the current image as ROIs

    Parameters
    ----------
    conn : BlitzGateway
        Gateway that handles the OMERO connection

    image : ImageWrapper
        The current OMERO image object

    label_image_list : list[dict]
        list of label image attributes dictionary, coming from cellprofiler pipeline

    save_label_list : list[dict]
        list of mask image files attributes dictionary ; coming from files saved on the local machine
    """

    update_service = conn.getUpdateService()

    # loop over the label images
    for label_image_attr in label_image_list:
        np_label_file = ""
        label_image = label_image_attr[COTI_OUTPUT_IMAGE_NAME]
        object_type = label_image_attr[COTI_OBJECT_TO_EXPORT]
        label_file = ""
        label_file_format = ""

        # find the corresponding file on the computer matching the current label image
        for save_label_attr in save_label_list:
            if label_image == save_label_attr[SI_IMAGE_SAVE]:
                label_file = save_label_attr[SI_FILE_NAME]
                label_file_format = save_label_attr[SI_FILE_FORMAT]
                break

        # Only save rois if a match is found
        if label_file != "":
            for file in os.listdir(cpprefs.get_default_output_directory()):
                if file.startswith(label_file) and file.endswith(label_file_format):
                    np_label_file = os.path.join(cpprefs.get_default_output_directory(), file)
                    break

            # segment the label image and extract ROIs
            np_label_image = ski.io.imread(np_label_file)
            contours = find_contours(np_label_image)

            for i, contour in enumerate(contours):
                # create a new polygon
                polygon = omero.model.PolygonI()
                polygon.theZ = rint(0)
                polygon.theT = rint(0)
                polygon.strokeWidth = omero.model.LengthI(1, omero.model.enums.UnitsLength.PIXEL)

                # add a comment with the index and type of object
                polygon.textValue = rstring(f"{object_type}#{i}")

                # create the polygon
                points = []
                for point in contour:
                    points.append(str(point[1]) + "," + str(point[0]))
                points = ", ".join(points)
                polygon.points = rstring(points)

                # Save the ROI (saves any linked shapes too)
                update_service.saveAndReturnObject(create_roi(image, [polygon]))


def create_roi(image, shapes):
    """
    Create a ROI object, linked it to the current image and populate it with a list of shapes

    Parameters
    ----------

    image : ImageWrapper
        The current OMERO image object

    shapes : list[omero.model.ShapeI]
        The current OMERO image object

    Returns
    -------
    """
    # create a ROI, link it to Image
    roi = omero.model.RoiI()
    roi.setImage(image._obj)

    # adding shapes to the ROI
    for shape in shapes:
        roi.addShape(shape)
    return roi


if __name__ == "__main__":
    list_argv = []
    app = QApplication(list_argv)
    window = MainWindow()
    window.show()
    app.exec()
