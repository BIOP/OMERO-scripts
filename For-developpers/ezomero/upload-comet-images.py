
import ezomero
import traceback
from PyQt6.QtWidgets import QLineEdit, QLabel, QFileDialog, QPushButton, QMainWindow, QVBoxLayout, \
    QWidget, QApplication, QHBoxLayout, QRadioButton, QButtonGroup

FONT_SIZE = 'font-size: 14px'
SEPARATOR = ","


class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()

        # main window settings
        self.setWindowTitle("Main window title")
        self.setMinimumSize(500, 100)
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

        # # Create radio buttons
        # self.radio_project_new = QRadioButton("New")
        # self.radio_project_existing = QRadioButton("Existing")
        # 
        # # Set the "Default" option selected by default
        # self.radio_project_new.setChecked(True)
        # 
        # # Group the radio buttons for accessibility
        # self.radio_group = QButtonGroup()
        # self.radio_group.addButton(self.radio_project_new)
        # self.radio_group.addButton(self.radio_project_existing)

        # project fields
        project_layout = QHBoxLayout()
        project_label = QLabel("Project")
        project_label.setStyleSheet(FONT_SIZE)
        self.project = QLineEdit()
        self.project.setStyleSheet(FONT_SIZE)
        project_widget = QWidget()
        project_layout.addWidget(project_label)
        project_layout.addWidget(self.project)
        project_widget.setLayout(project_layout)
        widgets.append(project_widget)

        # dataset fields
        dataset_layout = QHBoxLayout()
        dataset_label = QLabel("Dataset")
        dataset_label.setStyleSheet(FONT_SIZE)
        self.dataset = QLineEdit()
        self.dataset.setStyleSheet(FONT_SIZE)
        dataset_widget = QWidget()
        dataset_layout.addWidget(dataset_label)
        dataset_layout.addWidget(self.dataset)
        dataset_widget.setLayout(dataset_layout)
        widgets.append(dataset_widget)

        # folder fields
        folder_layout = QHBoxLayout()
        folder_label = QLabel("Image path")
        folder_label.setStyleSheet(FONT_SIZE)
        self.folder = QLineEdit()
        self.folder.setStyleSheet(FONT_SIZE)
        folder_button = QPushButton(text="Choose")
        folder_button.clicked.connect(self.open_file_chooser)
        folder_button.setStyleSheet(FONT_SIZE)
        folder_widget = QWidget()
        folder_layout.addWidget(folder_label)
        folder_layout.addWidget(self.folder)
        folder_layout.addWidget(folder_button)
        folder_widget.setLayout(folder_layout)
        widgets.append(folder_widget)
        
        # attachment fields
        att_layout = QHBoxLayout()
        att_label = QLabel("Attachment")
        att_label.setStyleSheet(FONT_SIZE)
        self.att = QLineEdit()
        self.att.setStyleSheet(FONT_SIZE)
        att_button = QPushButton(text="Choose")
        att_button.clicked.connect(self.open_multiple_file_chooser)
        att_button.setStyleSheet(FONT_SIZE)
        att_widget = QWidget()
        att_layout.addWidget(att_label)
        att_layout.addWidget(self.att)
        att_layout.addWidget(att_button)
        att_widget.setLayout(att_layout)
        widgets.append(att_widget)

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
        project = self.project.text()
        dataset = self.dataset.text()
        image_path = self.folder.text()
        attachments = self.att.text()
        self.close()
        run_script(username, password, project, dataset, image_path, attachments)

    def open_file_chooser(self):
        rsp_path_list, rsp_filter = QFileDialog.getOpenFileName(parent=self, caption="select an image")
        self.folder.setText(str(rsp_path_list))
        
    def open_multiple_file_chooser(self):
        rsp_path_list, rsp_filter = QFileDialog.getOpenFileNames(parent=self, caption="select one or more files")
        path_list = SEPARATOR.join(rsp_path_list)
        self.att.setText(str(path_list))


def run_script(username, password, project_name, dataset_name, image_path, attachments):
    host = "omero-server-poc.epfl.ch"
    conn = ezomero.connect(username, password, group="", host=host, port=4064, secure=True)
    conn.connect()

    if conn.isConnected():
        print(f"Connected to {host}")
        try:
            if project_name != "":
                print(f"Creating project '{project_name}'")
                project_id = ezomero.post_project(conn, project_name)

                if dataset_name != "":
                    print(f"Creating dataset '{dataset_name}'")
                    dataset_id = ezomero.post_dataset(conn, dataset_name, project_id=project_id)
                    image_ids = ezomero.ezimport(conn, image_path, dataset=dataset_id)

                    if len(image_ids) > 0:
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
