from omero.gateway import BlitzGateway
import omero
import traceback
from PyQt6.QtWidgets import QLineEdit, QLabel, QPushButton, QMainWindow, QVBoxLayout, \
    QWidget, QApplication, QHBoxLayout, QSpinBox


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

        # Image Id
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
        image_id = self.image_id.value()
        self.close()
        run_script(host, username, password, image_id)


def list_tags_from_image(conn, image_id):
    image_wrapper = conn.getObject("Image", image_id)
    annotation_list = image_wrapper.listAnnotations()
    tag_list = []
    for annotation in annotation_list:
        if annotation.OMERO_TYPE == omero.model.TagAnnotationI:
            tag_list.append(annotation.getTextValue())

    return tag_list


def add_tags(conn, image_id, tags):
    image = conn.getObject("Image", image_id)
    group_tags = conn.getObjects("TagAnnotation")
    image_tags = list_tags_from_image(conn, image.getId())
    tags_to_add = []
    for tag in tags:
        if tag.lower() not in [tag_to_add.getTextValue().lower() for tag_to_add in tags_to_add]:
            if tag.lower() not in [tag_to_add.getTextValue().lower() for tag_to_add in group_tags]:
                tag_wrapper = omero.gateway.TagAnnotationWrapper()
                tag_wrapper.setTextValue(tag)
                tag_wrapper.save()
            else:
               tag_wrapper = next(group_tag for group_tag in group_tags if group_tag.getTextValue.lower() == tag.lower())

            if tag_wrapper.getTextValue().lower() not in [tag_to_add.getTextValue().lower() for tag_to_add in image_tags]:
                tags_to_add.append(tag_wrapper)

            image.linkAnnotation(tag_wrapper)


def run_script(host, username, password, image_id):
    conn = BlitzGateway(username, password, host=host, port=4064, secure=True)
    conn.connect()

    new_tags = ["a", "b", "c"]

    if conn.isConnected():
        print(f"Connected to {host}")
        try:
            add_tags(conn, image_id, new_tags)
        except Exception as e:
            print(e)
            traceback.print_exc()
        finally:
            conn.close()
            print(f"Disconnected from {host}")


if __name__ == "__main__":
    list_argv = []
    app = QApplication(list_argv)
    window = MainWindow()
    window.show()
    app.exec()

