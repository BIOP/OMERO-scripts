"""
upload_comet_images.py
Code to import any image on OMERO and attach one or more files to it.
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
Created by Rémy Dornier - EPFL - BIOP
Date: 2025.03.12
Version: 1.0.0

Dependencies
    - PyQt6
    - ezomero
"""

import ezomero
import traceback
from PyQt6.QtWidgets import QLineEdit, QLabel, QFileDialog, QPushButton, QMainWindow, QVBoxLayout, \
    QWidget, QApplication, QHBoxLayout, QRadioButton, QButtonGroup, QComboBox


FONT_SIZE = 'font-size: 14px'
SEPARATOR = ","
NEW_PREFIX = "$new$"
FIXED_WIDTH = 300
HOST = "omero-server-poc.epfl.ch"

GROUP = "group"
DST_NAME = "datasetName"
PRJ_NAME= "projectName"
FOL_PATH = "path"
ATT_PATH = "attachment"


class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()

        self.is_connected = False
        self.conn = None
        self.host = HOST
        self.project_dict = {}
        self.dataset_dict = {}
        self.group_dict = {}
        self.upload_list = []

        # main window settings
        self.setWindowTitle("Main window title")
        self.setMinimumSize(600, 200)
        widgets = []
        main_layout = QVBoxLayout()

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

        # load groups
        self.load_group_button = QPushButton(text="Load OMERO groups")
        self.load_group_button.setStyleSheet(FONT_SIZE)
        self.load_group_button.clicked.connect(self.load_groups)
        widgets.append(self.load_group_button)

        # group fields
        group_layout = QHBoxLayout()
        group_label = QLabel("Group")
        group_label.setStyleSheet(FONT_SIZE)
        self.group_combo = QComboBox()
        self.group_combo.setStyleSheet(FONT_SIZE)
        group_widget = QWidget()
        group_layout.addWidget(group_label)
        group_layout.addWidget(self.group_combo)
        group_widget.setLayout(group_layout)
        widgets.append(group_widget)

        # connection button
        self.connect_button = QPushButton(text="Connect")
        self.connect_button.setStyleSheet(FONT_SIZE)
        self.connect_button.clicked.connect(self.connect_to_omero)
        self.connect_button.setEnabled(False)
        widgets.append(self.connect_button)

        # project fields
        project_group_layout = QHBoxLayout()
        project_label = QLabel("Project")
        project_label.setStyleSheet(FONT_SIZE)

        self.radio_project_new = QRadioButton("New")
        self.radio_project_existing = QRadioButton("Existing")
        self.radio_project_new.setEnabled(False)
        self.radio_project_existing.setEnabled(False)
        self.radio_project_new.setChecked(True)
        radio_project_group = QButtonGroup()
        radio_project_group.addButton(self.radio_project_new)
        radio_project_group.addButton(self.radio_project_existing)
        project_group_widget = QWidget()
        project_group_layout.addWidget(project_label)
        project_group_layout.addWidget(self.radio_project_new)
        project_group_layout.addWidget(self.radio_project_existing)
        project_group_widget.setLayout(project_group_layout)
        widgets.append(project_group_widget)

        project_layout = QHBoxLayout()
        self.project = QLineEdit()
        self.project.setEnabled(False)
        self.project.setStyleSheet(FONT_SIZE)
        self.project.setFixedWidth(FIXED_WIDTH)
        self.project_combo = QComboBox()
        self.project_combo.setEnabled(False)
        self.project_combo.setStyleSheet(FONT_SIZE)
        self.project_combo.currentTextChanged.connect(self.project_text_changed)
        project_widget = QWidget()
        project_layout.addWidget(self.project)
        project_layout.addWidget(self.project_combo)
        project_widget.setLayout(project_layout)
        widgets.append(project_widget)

        # dataset fields
        dataset_group_layout = QHBoxLayout()
        dataset_label = QLabel("Dataset")
        dataset_label.setStyleSheet(FONT_SIZE)

        self.radio_dataset_new = QRadioButton("New")
        self.radio_dataset_existing = QRadioButton("Existing")
        self.radio_dataset_existing.setEnabled(False)
        self.radio_dataset_new.setEnabled(False)
        self.radio_dataset_new.setChecked(True)
        radio_dataset_group = QButtonGroup()
        radio_dataset_group.addButton(self.radio_dataset_new)
        radio_dataset_group.addButton(self.radio_dataset_existing)
        dataset_group_widget = QWidget()
        dataset_group_layout.addWidget(dataset_label)
        dataset_group_layout.addWidget(self.radio_dataset_new)
        dataset_group_layout.addWidget(self.radio_dataset_existing)
        dataset_group_widget.setLayout(dataset_group_layout)
        widgets.append(dataset_group_widget)

        dataset_layout = QHBoxLayout()
        self.dataset = QLineEdit()
        self.dataset.setEnabled(False)
        self.dataset.setStyleSheet(FONT_SIZE)
        self.dataset.setFixedWidth(FIXED_WIDTH)
        self.dataset_combo = QComboBox()
        self.dataset_combo.setEnabled(False)
        self.dataset_combo.setStyleSheet(FONT_SIZE)
        dataset_widget = QWidget()
        dataset_layout.addWidget(self.dataset)
        dataset_layout.addWidget(self.dataset_combo)
        dataset_widget.setLayout(dataset_layout)
        widgets.append(dataset_widget)

        # folder fields
        folder_layout = QHBoxLayout()
        folder_label = QLabel("Image path")
        folder_label.setStyleSheet(FONT_SIZE)
        self.folder = QLineEdit()
        self.folder.setEnabled(False)
        self.folder.setStyleSheet(FONT_SIZE)
        self.folder_button = QPushButton(text="Choose")
        self.folder_button.setEnabled(False)
        self.folder_button.clicked.connect(self.open_file_chooser)
        self.folder_button.setStyleSheet(FONT_SIZE)
        folder_widget = QWidget()
        folder_layout.addWidget(folder_label)
        folder_layout.addWidget(self.folder)
        folder_layout.addWidget(self.folder_button)
        folder_widget.setLayout(folder_layout)
        widgets.append(folder_widget)
        
        # attachment fields
        att_layout = QHBoxLayout()
        att_label = QLabel("Attachment")
        att_label.setStyleSheet(FONT_SIZE)
        self.att = QLineEdit()
        self.att.setEnabled(False)
        self.att.setStyleSheet(FONT_SIZE)
        self.att_button = QPushButton(text="Choose")
        self.att_button.setEnabled(False)
        self.att_button.clicked.connect(self.open_multiple_file_chooser)
        self.att_button.setStyleSheet(FONT_SIZE)
        att_widget = QWidget()
        att_layout.addWidget(att_label)
        att_layout.addWidget(self.att)
        att_layout.addWidget(self.att_button)
        att_widget.setLayout(att_layout)
        widgets.append(att_widget)

        # buttons fields
        button_layout = QHBoxLayout()
        ok_button = QPushButton(text="OK")
        ok_button.setStyleSheet(FONT_SIZE)
        ok_button.clicked.connect(self.run_app)
        next_button = QPushButton(text="Next")
        next_button.setStyleSheet(FONT_SIZE)
        next_button.clicked.connect(self.next_upload)
        cancel_button = QPushButton(text="Cancel")
        cancel_button.setStyleSheet(FONT_SIZE)
        cancel_button.clicked.connect(self.close_app)
        button_widget = QWidget()
        button_layout.addWidget(ok_button)
        button_layout.addWidget(next_button)
        button_layout.addWidget(cancel_button)
        button_widget.setLayout(button_layout)
        widgets.append(button_widget)

        self.radio_project_existing.toggled.connect(self.existing_project_selected)
        self.radio_project_new.toggled.connect(self.new_project_selected)
        self.radio_dataset_existing.toggled.connect(self.existing_dataset_selected)
        self.radio_dataset_new.toggled.connect(self.new_dataset_selected)

        # building the main GUI
        for w in widgets:
            main_layout.addWidget(w)

        widget = QWidget()
        widget.setLayout(main_layout)

        # Set the central widget of the Window. Widget will expand
        # to take up all the space in the window by default.
        self.setCentralWidget(widget)


    def next_upload(self):
        # get the user input for the current upload
        selection = {}
        selection[GROUP] = self.group_combo.currentText()
        selection[ATT_PATH] = self.att.text()
        selection[FOL_PATH] = self.folder.text()
        if self.radio_project_new.isChecked():
            selection[PRJ_NAME] = NEW_PREFIX + self.project.text()
            self.project_dict[selection[PRJ_NAME]] = -1
        else:
            selection[PRJ_NAME] = self.project_combo.currentText()

        if self.radio_dataset_new.isChecked():
            selection[DST_NAME] = NEW_PREFIX + self.dataset.text()
            self.dataset_dict[selection[DST_NAME]] = -1
        else:
            selection[DST_NAME] = self.dataset_combo.currentText()

        selection[ATT_PATH] = self.att.text()
        self.upload_list.append(selection)

        # reset the gui
        self.folder.setText("")
        self.att.setText("")
        self.project.setText("")
        self.dataset.setText("")
        self.new_project_selected()
        self.new_dataset_selected()


    def close_app(self):
        if self.conn is not None and self.conn.isConnected:
            self.conn.close()
        self.close()


    def run_app(self):

        if self.folder.text() is not None and self.folder.text() != "":
            self.next_upload()

        username = self.username.text()
        password = self.password.text()

        self.close()

        run_script(self.conn, self.host, username, password, self.upload_list, self.project_dict, self.dataset_dict)


    def open_file_chooser(self):
        rsp_path_list, rsp_filter = QFileDialog.getOpenFileName(parent=self, caption="Select an image")
        self.folder.setText(str(rsp_path_list))


    def open_multiple_file_chooser(self):
        rsp_path_list, rsp_filter = QFileDialog.getOpenFileNames(parent=self, caption="Select one or more files")
        path_list = SEPARATOR.join(rsp_path_list)
        self.att.setText(str(path_list))


    def connect_to_omero(self):
        username = self.username.text()
        password = self.password.text()
        group = self.group_combo.currentText()
        self.conn.close()

        self.conn = ezomero.connect(username, password, group=f"{group}", host=self.host, port=4064, secure=True)

        if self.conn is not None and self.conn.isConnected():
            self.is_connected = True
            self.att.setEnabled(True)
            self.folder.setEnabled(True)
            self.folder_button.setEnabled(True)
            self.att_button.setEnabled(True)
            self.project.setEnabled(False)
            self.dataset.setEnabled(True)
            self.password.setEnabled(False)
            self.username.setEnabled(False)
            self.connect_button.setEnabled(False)
            self.group_combo.setEnabled(False)
            self.radio_project_new.setEnabled(True)
            self.radio_dataset_existing.setEnabled(True)
            self.radio_dataset_new.setEnabled(True)
            self.radio_project_existing.setEnabled(True)
            self.new_project_selected()

            project_names = sorted(self.list_projects(self.conn))
            for project_name in project_names:
                self.project_combo.addItem(project_name)
            if len(project_names) > 0:
                self.dataset_combo.setCurrentText(project_names[0])
                self.project_text_changed(project_names[0])


    def load_groups(self):
        username = self.username.text()
        password = self.password.text()
        self.conn = ezomero.connect(username, password, group="", host=self.host, port=4064, secure=True)

        if self.conn is not None and self.conn.isConnected():
            group_names = sorted(self.list_groups(self.conn))
            for group_name in group_names:
                self.group_combo.addItem(group_name)

            if len(group_names) > 0:
                group_name = self.conn.getEventContext().groupName
                self.group_combo.setCurrentText(group_name)
                self.load_group_button.setEnabled(False)
                self.connect_button.setEnabled(True)


    def list_groups(self, conn):
        # Retrieve the services we are going to use
        admin_service = conn.getAdminService()

        ec = admin_service.getEventContext()
        groups = [admin_service.getGroup(v) for v in ec.memberOfGroups]
        group_names = []
        for group in groups:
            if group.id.val in [0, 1, 2]:
                continue
            group_names.append(group.name.val)
            self.group_dict[group.name.val] = group.id.val
        return group_names


    def list_projects(self, conn):
        projects = conn.getObjects("Project", opts={'owner': conn.getUser().getId()})
        project_names = []
        for project in projects:
            project_names.append(project.getName())
            self.project_dict[project.getName()] = project.getId()
        return project_names


    def list_datasets(self, conn, project_id):
        project = conn.getObject("Project", project_id)
        dataset_names = []
        for dataset in project.listChildren():
            dataset_names.append(dataset.getName())
            self.dataset_dict[dataset.getName()] = dataset.getId()
        return dataset_names


    def new_project_selected(self):
        self.project.setEnabled(self.radio_project_new.isChecked())
        self.project_combo.setEnabled(not self.radio_project_new.isChecked())
        self.dataset.setEnabled(self.radio_project_new.isChecked())
        self.dataset_combo.setEnabled(not self.radio_project_new.isChecked())
        self.radio_dataset_new.setEnabled(not self.radio_project_new.isChecked())
        self.radio_dataset_existing.setEnabled(not self.radio_project_new.isChecked())
        self.radio_dataset_new.setChecked(self.radio_project_new.isChecked())
        self.radio_dataset_existing.setChecked(not self.radio_project_new.isChecked())


    def existing_project_selected(self):
        self.project.setEnabled(not self.radio_project_existing.isChecked())
        self.project_combo.setEnabled(self.radio_project_existing.isChecked())
        self.dataset.setEnabled(self.radio_project_existing.isChecked())
        self.dataset_combo.setEnabled(not self.radio_project_existing.isChecked())
        self.radio_dataset_new.setEnabled(self.radio_project_existing.isChecked())
        self.radio_dataset_new.setChecked(self.radio_project_existing.isChecked())
        self.radio_dataset_existing.setEnabled(self.radio_project_existing.isChecked())
        self.radio_dataset_existing.setChecked(not self.radio_project_existing.isChecked())


    def new_dataset_selected(self):
        self.dataset.setEnabled(self.radio_dataset_new.isChecked())
        self.dataset_combo.setEnabled(not self.radio_dataset_new.isChecked())


    def existing_dataset_selected(self):
        self.dataset.setEnabled(not self.radio_dataset_existing.isChecked())
        self.dataset_combo.setEnabled(self.radio_dataset_existing.isChecked())


    def project_text_changed(self, s):
        dataset_names = sorted(self.list_datasets(self.conn, self.project_dict[s]))
        self.dataset_combo.clear()
        for dataset_name in dataset_names:
            self.dataset_combo.addItem(dataset_name)
        if len(dataset_names) > 0:
            self.dataset_combo.setCurrentText(dataset_names[0])


def run_script(conn, host, username, password, upload_task_list, project_dict, dataset_dict):

    if conn is not None and conn.isConnected():
        print(f"Connected to {host}")
        try:
            for upload_task in upload_task_list:
                project_name = upload_task[PRJ_NAME]
                dataset_name = upload_task[DST_NAME]
                group = upload_task[GROUP]
                attachments = upload_task[ATT_PATH]
                image_path = upload_task[FOL_PATH]

                if project_name is not None and project_name != "":
                    # get or create the project
                    if project_dict[project_name] < 0:
                        real_name = project_name.replace(NEW_PREFIX, "")
                        print(f"Creating project '{real_name}'")
                        project_id = ezomero.post_project(conn, real_name)
                    else:
                        project_id = project_dict[project_name]

                    if dataset_name is not None and dataset_name != "":
                        # get or create the dataset
                        if dataset_dict[dataset_name] < 0:
                            real_name = dataset_name.replace(NEW_PREFIX, "")
                            print(f"Creating dataset '{real_name}'")
                            dataset_id = ezomero.post_dataset(conn, real_name, project_id=project_id)
                        else:
                            dataset_id = dataset_dict[dataset_name]

                        # importing the image in the right dataset

                        image_ids = ezomero.ezimport(conn, image_path, dataset=dataset_id)

                        # because the upload can be long, we need to re-connect again to omero
                        if conn is None or not conn.isConnected():
                            print(f"Reconnection to {host}...")
                            conn = ezomero.connect(username, password, group=group, host=host, port=4064, secure=True)
                            print(f"Reconnected...")

                        # attaching the file(s) to the image
                        if len(image_ids) > 0 and attachments is not None and attachments != "":
                            image = conn.getObject("Image", image_ids[0])

                            attachments_list = attachments.split(SEPARATOR)
                            for att in attachments_list:
                                mimetype = None
                                if att.endswith(".pdf"):
                                    mimetype = "application/pdf"

                                file_ann = conn.createFileAnnfromLocalFile(att, mimetype=mimetype)
                                print(f"Attaching FileAnnotation to Image: ", image.getId(), "File ID:", file_ann.getId(),
                                      ",", file_ann.getFile().getName(), "Size:", file_ann.getFile().getSize())
                                image.linkAnnotation(file_ann)  # link it to image.
                        else:
                            print("ERROR: images cannot be uploaded on OMERO : an error occurred during the import")
                    else:
                        print("Give a valid name to the dataset !")
                else:
                    print("Give a valid name to the project !")

        except Exception as e:
            print(e)
            traceback.print_exc()
        finally:
            conn.close()
            print(f"Disconnect from {host}")


if __name__ == "__main__":
    list_argv = []
    app = QApplication(list_argv)
    window = MainWindow()
    window.show()
    app.exec()
