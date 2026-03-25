from omero.gateway import BlitzGateway
import omero
from omero.rtypes import wrap
import traceback
from PyQt6.QtWidgets import QLineEdit, QLabel, QPushButton, QMainWindow, QVBoxLayout, \
    QWidget, QApplication, QHBoxLayout


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
            '''
            Scenario 1
            -- hard-coded everything
            -- working in the default group only
            '''
            params = omero.sys.ParametersI()
            qs = conn.getQueryService()
            q = "select shape.id from Shape shape where shape.textValue = 'test'"

            results = qs.projection(q, params, conn.SERVICE_OPTS)
            [print(result[0].val) for result in results]


            '''
            Scenario 2
            -- with where_clause & params
            -- working in the default group only
            '''
            where_clause = []

            params.add('filter', wrap([f"'Figure_{201}%'"]))
            where_clause.append(f"mv.name like 'Figure_{205}%'")

            params.add('ns', wrap(["omero.web.figure.link"]))
            where_clause.append(f"a.ns in (:ns)")
            where_clause.append("mv.value != '' ")

            qs = conn.getQueryService()
            q = """
                        select distinct a.id, mv.name
                            from Annotation a
                            join a.mapValue mv where %s
                        """ % (" and ".join(where_clause))

            results = qs.projection(q, params, conn.SERVICE_OPTS)
            [print(result[0].val) for result in results]
            [print(result[1].val) for result in results]


            '''
            Scenario 3
            -- to work on multiple groups
            '''
            # Retrieve the services we are going to use
            admin_service = conn.getAdminService()

            ec = admin_service.getEventContext()
            groups = [admin_service.getGroup(v) for v in ec.memberOfGroups]
            for group in groups:
                print('Searching in group: %s' % group.name.val)
                conn.SERVICE_OPTS.setOmeroGroup(group.id.val)

                params = omero.sys.ParametersI()
                qs = conn.getQueryService()
                q = "select shape from Shape shape where shape.textValue = 'test2'"

                results = qs.findAllByQuery(q, params, conn.SERVICE_OPTS)
                [print(result) for result in results]

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


"""
3 getters (see https://forum.image.sc/t/sql-query-failed/100712/3)
- qs.findAllByQuery(query, omero.sys.ParametersI(), conn.SERVICE_OPTS)
- qs.findByQuery(query, omero.sys.ParametersI(), conn.SERVICE_OPTS)
- qs.projection(query, omero.sys.ParametersI(), conn.SERVICE_OPTS)
"""

"""
Other query examples

- f"select roi.id from Roi as roi where roi.image = {object_id}"
- "select distinct map.value from Annotation ann join ann.mapValue map where map.name = 'ArgoSlide_name'"

# Complex query with sub queries
- "select a from Annotation a where a.id in (select link.child from AnnotationAnnotationLink link " \
  "where link.parent in (select ann.id from Annotation ann where ann.ns='openmicroscopy.org/omero/insight/tagset' " \
  "and ann.textValue='protein-atlas-set'))"


# Complex query using 'distinct', 'group by' & 'having' keywords
-   query_parts = ["ImageAnnotationLink link"]
    conditions = ["link.child=" + str(common_tag_id)]

    for i, tag_id in enumerate(self.tag_list):
        query_parts.append("ImageAnnotationLink link%s" % (i + 1))
        conditions.append("link.parent = link%s.parent" % (i + 1))
        conditions.append("link%s.child=%s" % (i + 1, tag_id))

    final_part = ""
    if is_exclusive:
        final_part = " and p.id in (%s) group by c.id having count(c) = %s" % (
            ",".join([str(dst_id) for dst_id in self.dataset_list]), str(len(self.tag_list)))
    
    intermediate_query = "select link.parent from %s where %s" % (", ".join(query_parts), " and ".join(conditions))
    
    "select distinct c.id from DatasetImageLink dil join dil.child c join dil.parent p where dil.child in (%s)%s" % (intermediate_query, final_part)



"""