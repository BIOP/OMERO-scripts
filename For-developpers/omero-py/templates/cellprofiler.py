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
# import os
# import tempfile
# import warnings
#
# from getpass import getpass
#
# # Import OMERO Python BlitzGateway
# from omero.gateway import BlitzGateway
#
# # Import Cell Profiler Dependencies
# import cellprofiler as cp
# import cellprofiler_core.preferences as cpprefs
# from cellprofiler_core.pipeline import Pipeline
#
# # Important to set when running headless
# cpprefs.set_headless()  # noqa
#
# # module used to inject OMERO image planes into Cell Profiler Pipeline
# from cellprofiler_core.modules.injectimage import InjectImage
#
#
# # Connect to the server
# def connect(hostname, username, password):
#     conn = BlitzGateway(username, password,
#                         host=hostname, port=4064, secure=True)
#     conn.connect()
#     return conn
#
#
# # Load-data
# def load_dataset(conn, dataset_id):
#     return conn.getObject("Dataset", dataset_id)
#
#
# # Load-pipeline
# def load_pipeline(pipeline_path):
#     pipeline = Pipeline()
#     pipeline.load(pipeline_path)
#     # Remove first 4 modules: Images, Metadata, NamesAndTypes, Groups...
#     # (replaced by InjectImage module below)
#     for i in range(4):
#         print('Remove module: ', pipeline.modules()[0].module_name)
#         pipeline.remove_module(1)
#     print('Pipeline modules:')
#     for module in pipeline.modules():
#         print(module.module_name)
#     return pipeline
#
#
# # Analyze-data
# def analyze(dataset, pipeline):
#     warnings.filterwarnings('ignore')
#     print("analyzing...")
#     # Set Cell Output Directory
#     new_output_directory = "C:\\Users\\dornier\\Downloads\\ExampleHuman"#os.path.normcase(tempfile.mkdtemp())
#     cpprefs.set_default_output_directory(new_output_directory)
#
#     files = list()
#     images = list(dataset.listChildren())
#     for count, image in enumerate(images):
#         print(image.getName())
#         pixels = image.getPrimaryPixels()
#         size_c = image.getSizeC()
#         # For each Image in OMERO, we copy pipeline and inject image modules
#         pipeline_copy = pipeline.copy()
#         # Inject image for each Channel (pipeline only handles 2 channels)
#         for c in range(0, size_c):
#             plane = pixels.getPlane(0, c, 0)
#             image_name = image.getName()
#             # Name of the channel expected in the pipeline
#             if c == 0:
#                 image_name = 'cellbody'
#             if c == 1:
#                 image_name = 'DNA'
#             if c == 2:
#                 image_name = 'PH3'
#             inject_image_module = InjectImage(image_name, plane)
#             inject_image_module.set_module_num(1)
#             pipeline_copy.add_module(inject_image_module)
#         pipeline_copy.run()
#
#         # Results obtained as CSV from Cell Profiler
#         path = new_output_directory + '/Nuclei.csv'
#         files.append(path)
#         path = new_output_directory + '/Image.csv'
#         files.append(path)
#     print("analysis done")
#     return files
#
#
# # Save-results
# def save_results(conn, files, dataset):
#     # Upload the CSV files
#     print("saving results...")
#     namespace = "cellprofiler.demo.namespace"
#     for f in files:
#         ann = conn.createFileAnnfromLocalFile(f, mimetype="text/csv",
#                                               ns=namespace, desc=None)
#         dataset.linkAnnotation(ann)
#
#
# # Disconnect
# def disconnect(conn):
#     conn.close()


import cellprofiler_core.pipeline
import cellprofiler_core.preferences
import cellprofiler_core.utilities.java
import pathlib



# main
def main():
    cellprofiler_core.preferences.set_headless()
    pipeline = cellprofiler_core.pipeline.Pipeline()

    current_dir = pathlib.Path().absolute()
    cellprofiler_core.preferences.set_default_output_directory(f"{current_dir}")

    pipeline.load("C:\\Users\\dornier\\Downloads\\ExampleHuman\\ExampleHuman\\ExampleHuman.cppipe")


    # # Collect user credentials
    # host = "omero-server-poc.epfl.ch"
    # username = "jd"
    # password = "0987654321"
    # dataset_id = "2715"
    #
    # # Connect to the server
    # conn = connect(host, username, password)
    #
    # # Read the pipeline
    # pipeline_path = "C:\\Users\\dornier\\Downloads\\ExampleHuman\\ExampleHuman\\ExampleHuman.cppipe"
    # pipeline = load_pipeline(pipeline_path)
    #
    # # Load the dataset
    # dataset = load_dataset(conn, dataset_id)
    #
    # files = analyze(dataset, pipeline)
    #
    # save_results(conn, files, dataset)
    # disconnect(conn)
    # print("done")


if __name__ == "__main__":
    main()
