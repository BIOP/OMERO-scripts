import omero
from omero.gateway import BlitzGateway
import traceback
from PyQt6.QtWidgets import QLineEdit, QLabel, QPushButton, QMainWindow, QVBoxLayout, \
    QWidget, QApplication, QHBoxLayout, QFileDialog, QSpinBox
import os


FONT_SIZE = 'font-size: 14px'
DEFAULT_HOST = 'omero-server.epfl.ch'
DEFAULT_LIMIT = 200


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

        # inactive day fields
        image_id_layout = QHBoxLayout()
        image_id_label = QLabel("Image Id")
        image_id_label.setStyleSheet(FONT_SIZE)
        self.image_id = QSpinBox()
        self.image_id.setStyleSheet(FONT_SIZE)
        self.image_id.setMinimum(0)
        self.image_id.setMaximum(2147483647)
        self.image_id.setSingleStep(1)
        image_id_widget = QWidget()
        image_id_layout.addWidget(image_id_label)
        image_id_layout.addWidget(self.image_id)
        image_id_widget.setLayout(image_id_layout)
        widgets.append(image_id_widget)

        # folder fields
        folder_layout = QHBoxLayout()
        folder_label = QLabel("Saving folder")
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
        output_path = self.folder.text()
        image_id = self.image_id.value()
        self.close()
        run_script(host, username, password, image_id, output_path)

    def open_file_chooser(self):
        response = QFileDialog.getExistingDirectory(parent=self, caption="select a folder", directory=os.getcwd())
        self.folder.setText(str(response))


def run_script(host, username, password, image_id, output_path):
    conn = BlitzGateway(username, password, host=host, port=4064, secure=True)
    conn.connect()

    if conn.isConnected():
        print(f"Connected to {host}")
        try:
            image = conn.getObject("Image", image_id)
            process_attachment(image, output_path)
        except Exception as e:
            print(e)
            traceback.print_exc()
        finally:
            conn.close()
            print(f"Disconnected from {host}")


def copy_attachment(file_path, ann):
    """
    Do a hard copy of the current file

    Parameters
    ----------
    file_path: str
        absolute path of the annotation file to copy
    ann: omero.model.Annotation
        file to copy

    Returns
    -------

    """
    with open(str(file_path), 'wb') as f:
        print("Copying file to", file_path, "...")
        for chunk in ann.getFileInChunks():
            f.write(chunk)


def process_attachment(container, output_path):
    """
    Do a hard copy of all the attachments linked to the current object

    Parameters
    ----------
    container: omero.model.Object
        The object to get attachments from
    output_path: str
        path of the output file
    Returns
    -------

    """
    for ann in container.listAnnotations():
        # only process attachments, not other types of annotations
        if ann.OMERO_TYPE == omero.model.FileAnnotationI:
            file_path = os.path.join(output_path, f"{ann.getFile().getId()}_{ann.getFile().getName()}")
            if not os.path.exists(file_path):
                try:
                    # do a hard copy of the attachment, with right name & extension
                    # i.e. human-readable
                    copy_attachment(file_path, ann)
                except Exception as e:
                    print(f"ERROR: cannot copy attachment for {container.OMERO_CLASS} {container.getId()}: {e}")


if __name__ == "__main__":
    list_argv = []
    app = QApplication(list_argv)
    window = MainWindow()
    window.show()
    app.exec()
