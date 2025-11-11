from omero.gateway import BlitzGateway
from omero.cli import cli_login
from omero.plugins.fs import FsControl
from omero.plugins.delete import DeleteControl
import traceback
from io import StringIO
import sys
from PyQt6.QtWidgets import QLineEdit, QLabel, QPushButton, QMainWindow, QVBoxLayout, \
    QWidget, QApplication, QHBoxLayout, QSpinBox, QCheckBox
from pathlib import Path
import os
from datetime import datetime


# Class to capture the output of the CLI command
class Capturing(list):
    def __enter__(self):
        self._stdout = sys.stdout
        sys.stdout = self._stringio = StringIO()
        return self

    def __exit__(self, *args):
        self.extend(self._stringio.getvalue().splitlines())
        del self._stringio    # free up some memory
        sys.stdout = self._stdout


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
        
        # Query limit fields
        query_limit_layout = QHBoxLayout()
        query_limit_label = QLabel("Limit size of the query")
        query_limit_label.setStyleSheet(FONT_SIZE)
        self.query_limits = QSpinBox()
        self.query_limits.setStyleSheet(FONT_SIZE)
        self.query_limits.setMinimum(0)
        self.query_limits.setMaximum(10000000)
        self.query_limits.setValue(DEFAULT_LIMIT)
        self.query_limits.setSingleStep(1)
        query_limit_widget = QWidget()
        query_limit_layout.addWidget(query_limit_label)
        query_limit_layout.addWidget(self.query_limits)
        query_limit_widget.setLayout(query_limit_layout)
        widgets.append(query_limit_widget)

        # Dry run fields
        dry_run_layout = QHBoxLayout()
        self.dry_run = QCheckBox()
        self.dry_run.setStyleSheet(FONT_SIZE)
        self.dry_run.setText("Dry run")
        self.dry_run.setChecked(True)
        dry_run_widget = QWidget()
        dry_run_layout.addWidget(self.dry_run)
        dry_run_widget.setLayout(dry_run_layout)
        widgets.append(dry_run_widget)

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
        dry_run = self.dry_run.isChecked()
        query_limit = self.query_limits.value()
        self.close()
        run_script(host, username, password, query_limit, dry_run)


def run_script(host, username, password, query_limit, dry_run):
    """
    Connect to OMERO, list the filesets not linked to any Image object and delete them if specified

    Parameters
    ----------
    host            String: name of the OMERO server
    username        String: username of the OMERO user
    password        String: password of the OMERO user
    query_limit     Integer: size of the filesets to retrieve from OMERO.
                            Should not be too large to not overload the server
    dry_run         Boolean: True to NOT delete files on the server

    Returns
    -------

    """
    with cli_login("-u", username, "-w", password, "-s", host, "-p", "4064") as cli:
        # connect to OMERO server
        conn = BlitzGateway(client_obj=cli.get_client())

        if conn.isConnected():
            print(f"Connected to {host}")
            try:
                print("Getting filesets not linked to any images...")
                cli.register('fs', FsControl, '_')
                cli.register('delete', DeleteControl, '_')

                import_args = ["fs",
                               "sets",
                               "--without-images",
                               "--limit",
                               str(query_limit)]

                try:
                    with Capturing() as output:
                        cli.invoke(import_args, strict=True)

                    print("SUCCESS", f"Got all filesets not linked to any OMERO image object")
                except PermissionError as err:
                    print(f"Error during fs reading {err}")

                # Prepare Delete command
                print("Preparing the deletion of filesets not linked to any images...")
                fs_ids = extract_fs_ids(output)
                import_args = create_delete_command(fs_ids, dry_run)

                try:
                    # Run delete
                    with Capturing() as output2:
                        cli.invoke(import_args, strict=True)
                    if not dry_run:
                        print("SUCCESS", "Deleted all filesets not linked to any OMERO image object")
                except PermissionError as err:
                    print(f"Error during fs reading {err}")

                print("Saving report in the Downloads...")
                save_report(output2, "Delete-fileset-logs")
                print("SUCCESS", "Report saved !")

                # closing CLI session
                cli.get_client().closeSession()

                # delete variables to free some memory
                del output
                del output2
            except Exception as e:
                print(e)
                traceback.print_exc()
            finally:
                conn.close()
                print(f"Disconnected from {host}")


def extract_fs_ids(output):
    """
    Read the Fileset IDs from the CLI output

    Parameters
    ----------
    output: logs from the CLI output

    Returns
    -------
    fs_ids: list of fileset ids

    """
    fs_ids = []
    for i in range(2,len(output)-1):
        line = output[i]
        fs_ids.append(line.split("|")[1].strip())
    return fs_ids


def create_delete_command(fs_ids, dry_run):
    """
    Create the list of input arguments to be passed to the CLI in order to delete
    filesets on OMERO.

    Parameters
    ----------
    fs_ids: list of fileset ids
    dry_run: True to NOT delete file on the server

    Returns
    -------
    input_args: List of CLI arguments to delete filesets. Adding --dry-run if specified.

    """
    input_args = ["delete", "--report"]
    if dry_run:
        input_args.append("--dry-run")

    for fs_id in fs_ids:
        input_args.append(f"Fileset:{fs_id}")
    return input_args


def save_report(report, name):
    """
    Save the output logs of the CLI in the Downloads folder,
    with the date-time of the current run.

    Parameters
    ----------
    name: String: Name of the text file
    report: List of CLI logs

    Returns
    -------

    """
    # Get the current date and time
    now = datetime.now()
    format_code = "%Y-%m-%d_%Hh%Mm%Ss"
    formatted_datetime = now.strftime(format_code)
    file_name = f"{formatted_datetime}-{name}.txt"
    
    # save the logs
    with open(os.path.join(Path.home(), "Downloads", file_name), "w") as f:
        f.write("\n".join(report))


if __name__ == "__main__":
    list_argv = []
    app = QApplication(list_argv)
    window = MainWindow()
    window.show()
    app.exec()
