from typing import List
import traceback
from omero.gateway import BlitzGateway
from PyQt6.QtWidgets import QLineEdit, QLabel, QPushButton, QMainWindow, QVBoxLayout, \
    QWidget, QApplication, QHBoxLayout, QSpinBox
import omero
import sys
from omero.cmd import (DiskUsage2, DiskUsage2Response, HandlePrx)

"""
Code copied and adapted from https://github.com/ome/omero-demo-cleanup/blob/main/src/omero_demo_cleanup/library.py
"""

FONT_SIZE = 'font-size: 14px'
DEFAULT_HOST = 'omero-server-poc.epfl.ch'

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
        user_id_layout = QHBoxLayout()
        user_id_label = QLabel("User Id")
        user_id_label.setStyleSheet(FONT_SIZE)
        self.user_id = QSpinBox()
        self.user_id.setStyleSheet(FONT_SIZE)
        self.user_id.setMinimum(0)
        self.user_id.setMaximum(2147483647)
        self.user_id.setSingleStep(1)
        user_id_widget = QWidget()
        user_id_layout.addWidget(user_id_label)
        user_id_layout.addWidget(self.user_id)
        user_id_widget.setLayout(user_id_layout)
        widgets.append(user_id_widget)

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
        user_id = self.user_id.value()
        self.close()
        run_script(host, username, password, user_id)


def run_script(host, username, password, user_id):
    conn = BlitzGateway(username, password, host=host, port=4064, secure=True)
    conn.connect()

    if conn.isConnected():
        print(f"Connected to {host}")
        try:
            conn.SERVICE_OPTS.setOmeroGroup(-1)
            qs = conn.getQueryService()
            print(f'Counting OMERO objects for user #{user_id}')

            # getting nImages
            query = f"SELECT COUNT(*) FROM Image WHERE owner_id = {user_id}"
            user_img_count = get_object_count(conn, qs, query)

            # getting nDatasets
            query = f"SELECT COUNT(*) FROM Dataset WHERE owner_id = {user_id}"
            user_dataset_count = get_object_count(conn, qs, query)

            # getting nProjects
            query = f"SELECT COUNT(*) FROM Project WHERE owner_id = {user_id}"
            user_project_count = get_object_count(conn, qs, query)

            # getting nScreens
            query = f"SELECT COUNT(*) FROM Screen WHERE owner_id = {user_id}"
            user_screen_count = get_object_count(conn, qs, query)

            # getting nPlates
            query = f"SELECT COUNT(*) FROM Plate WHERE owner_id = {user_id}"
            user_plate_count = get_object_count(conn, qs, query)

            # getting nTags
            query = f"SELECT COUNT(*) FROM Annotation WHERE owner_id = {user_id} and discriminator = '/basic/text/tag/'"
            user_tag_count = get_object_count(conn, qs, query)

            # getting nFigures
            query = f"SELECT COUNT(*) FROM Annotation WHERE owner_id = {user_id} and discriminator='/type/OriginalFile/' and ns = 'omero.web.figure.json'"
            user_figure_count = get_object_count(conn, qs, query)

            # getting nAttachments
            query = f"SELECT COUNT(*) FROM Annotation WHERE owner_id = {user_id} and discriminator='/type/OriginalFile/' and ns <> 'omero.web.figure.json'"
            user_attachment_count = get_object_count(conn, qs, query)

            # getting disk usage for the current user (nTotal files and total size in bytes)
            user_stats = resource_usage(conn, user_id)

            # create a nice summary
            file_content = create_file(user_stats, user_img_count, user_dataset_count, user_project_count,
                                       user_screen_count, user_plate_count, user_tag_count, user_figure_count,
                                       user_attachment_count)
            print("______________________________________________")
            print(f"Found {len(user_stats)} users")
            print(file_content)
            print("______________________________________________")

        except Exception as e:
            print(e)
            traceback.print_exc()
        finally:
            conn.close()
            print(f"Disconnected from {host}")


def create_file(user_stats, img_count, dst_count, prj_count, scr_count, plt_count, tag_count, fgr_count, att_count):
    content = ("user_id,username,totalFileSize (GB),nTotalFiles,nImages,nDatasets,nProjects,"
               "nScreens,nPlates,nTags,nFigure,nAttachments\n")
    for user in user_stats:
        content += (f"{user.id},{user.name},{user.size/1000000000},{user.count},{img_count}"
                    f",{dst_count},{prj_count},{scr_count},{plt_count},{tag_count},{fgr_count},{att_count}\n")

    return content


class UserStats:
    # Represents a user and their resource usage.
    # "is_worse_than" defines a strict partial order.
    # copied this code from https://github.com/ome/omero-demo-cleanup/blob/main/src/omero_demo_cleanup/library.py

    def __init__(
        self, user_id: int, name: str, count: int, size: int
    ) -> None:
        self.id = user_id
        self.name = name
        self.count = count
        self.size = size

    def is_worse_than(self, other: "UserStats") -> bool:
        if (
            other.count > self.count
            or other.size > self.size
        ):
            return False
        return (
            self.count > other.count
            or self.size > other.size
        )


def get_object_count(conn, qs, q):
    params = omero.sys.ParametersI()
    results = qs.projection(q, params, conn.SERVICE_OPTS)

    return results[0][0].val


def resource_usage(conn: BlitzGateway, experimenter_id) -> List[UserStats]:
    # Note users' resource usage.
    # DiskUsage2.targetClasses remains too inefficient so iterate.
    # copied this code from https://github.com/ome/omero-demo-cleanup/blob/main/src/omero_demo_cleanup/library.py

    user_stats = []
    user_info = {}
    if experimenter_id > 0:
        user = conn.getObject("Experimenter", experimenter_id)
        user_info[user.getId()] = user.getOmeName()
    else:
        for result in conn.getQueryService().projection(
                "SELECT id, omeName FROM Experimenter", None
        ):
            user_id = result[0].val
            user_name = result[1].val
            user_info[user_id] = user_name
        
    for user_id, user_name in user_info.items():
        print(f'Finding disk usage of "{user_name}" (#{user_id}).')
        user = {"Experimenter": [user_id]}
        rsp = submit(conn, DiskUsage2(targetObjects=user), DiskUsage2Response)

        file_count = 0
        file_size = 0

        for who, usage in rsp.totalFileCount.items():
            if who.first == user_id:
                file_count += usage
        for who, usage in rsp.totalBytesUsed.items():
            if who.first == user_id:
                file_size += usage

        if file_count > 0 or file_size > 0:
            user_stats.append(
                UserStats(user_id, user_name, file_count, file_size)
            )
    return user_stats


def submit(
    conn: BlitzGateway, request, expected
) -> HandlePrx:
    # Submit a request and wait for it to complete.
    # Returns with the response only if it was of the given type.
    # copied this code from https://github.com/ome/omero-demo-cleanup/blob/main/src/omero_demo_cleanup/library.py

    cb = conn.c.submit(request, loops=500)
    try:
        rsp = cb.getResponse()
    finally:
        cb.close(True)

    if not isinstance(rsp, expected):
        conn._closeSession()
        sys.exit(f"unexpected response: {rsp}")
    return rsp


if __name__ == "__main__":
    list_argv = []
    app = QApplication(list_argv)
    window = MainWindow()
    window.show()
    app.exec()


