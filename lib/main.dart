// ignore_for_file: depend_on_referenced_packages

import 'package:facerecognition_flutter/utils/color_utils.dart';
import 'package:flutter/material.dart';
import 'dart:async';
import 'dart:math';
import 'package:facesdk_plugin/facesdk_plugin.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';
import 'package:flutter_exif_rotation/flutter_exif_rotation.dart';
import 'package:path/path.dart';
import 'package:sqflite/sqflite.dart';
import 'package:fluttertoast/fluttertoast.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'dart:io' show Platform;
import 'about.dart';
import 'settings.dart';
import 'model/person.dart';
import 'personview.dart';
import 'facedetectionview.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp, // Lock to portrait mode
  ]).then((_) {
    runApp(MyApp());
  });
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
        title: 'Face Recognition',
        theme: ThemeData(
          // Define the default brightness and colors.
          useMaterial3: true,
          brightness: Brightness.dark,
          scaffoldBackgroundColor: ColorUtils.background1,
          appBarTheme: AppBarTheme(
            backgroundColor: ColorUtils.background1,
          ),
          primaryColor: ColorUtils.pink,
        ),
        home: MyHomePage(title: 'Face Recognition'));
  }
}

// ignore: must_be_immutable
class MyHomePage extends StatefulWidget {
  final String title;
  var personList = <Person>[];

  MyHomePage({super.key, required this.title});

  @override
  MyHomePageState createState() => MyHomePageState();
}

class MyHomePageState extends State<MyHomePage> {
  String _warningState = "";
  bool _visibleWarning = false;

  final _facesdkPlugin = FacesdkPlugin();

  @override
  void initState() {
    super.initState();

    init();
  }

  Future<void> init() async {
    int facepluginState = -1;
    String warningState = "";
    bool visibleWarning = false;

    try {
      if (Platform.isAndroid) {
        await _facesdkPlugin
            .setActivation(
                "hg5pHCISumInoMXoR1FBlTDn9U7u6wNOmv+lV3WB2iM5v1v3LIgj6OmlOKL5j3tnDXx3UGIkWYelhFscPLZ9GdsQ7fgok1EpbBHvlsGDgxGA+smhrjttgoJB1CFdfI04g8tZcWFHneKCHaJgQ+rCLmrSIkzs9O2M5hivXUY+kb3wgtkWiL4vbXDUUdKkQjAh7dMvfrURZ1MtESp7meoTeUofqSD1eqrLSaR3iFcDMrUukPbjpwJ6hQonYkpAWTInTh8osptISPJsdu0+hyUzo3ITPXk/nck9++BaPa+HlRcDrOZCxOKjIcZzOH7QZ/yg7GsGQYDqmiWKSI6JWUJ/xQ==")
            .then((value) => facepluginState = value ?? -1);
      } else {
        await _facesdkPlugin
            .setActivation(
                "V6WBQAdNzrxJVEQFKUnfBU2narsb0locsRYMVkYEtYQtgZ7mlDzfYxZHpHegx+4mvsdA8xo4diouRRQaMDTcXvZds9/7daewYx+94Yftrfiem2bDtkeL/2Y8NhAIfuZg2f306T4JOG0ZUYbIVigiAw6pKpOAAeH82If4+w6HIsp326wCFRxK6LBq9+WoDeymFklkqI+BmNUDXv4g9q8DSx9IGpi0lMnIkJY2cE3kisDZ4vKUQMOXNEMvehPvzcKgDw1yuxFqAyJxoVAevoirZ+H2l2lGOaikXPQFnh5Y18mfULw46HjLtI8+z+8AClQIQfU5Hb4gpjiWp5pzNgP6Qg==")
            .then((value) => facepluginState = value ?? -1);
      }

      if (facepluginState == 0) {
        await _facesdkPlugin
            .init()
            .then((value) => facepluginState = value ?? -1);
      }
    } catch (e) {}

    List<Person> personList = await loadAllPersons();
    await SettingsPageState.initSettings();

    final prefs = await SharedPreferences.getInstance();
    int? livenessLevel = prefs.getInt("liveness_level");

    try {
      await _facesdkPlugin
          .setParam({'check_liveness_level': livenessLevel ?? 0});
    } catch (e) {
      print(e);
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    if (facepluginState == -1) {
      warningState = "Invalid license!";
      visibleWarning = true;
    } else if (facepluginState == -2) {
      warningState = "License expired!";
      visibleWarning = true;
    } else if (facepluginState == -3) {
      warningState = "Invalid license!";
      visibleWarning = true;
    } else if (facepluginState == -4) {
      warningState = "No activated!";
      visibleWarning = true;
    } else if (facepluginState == -5) {
      warningState = "Init error!";
      visibleWarning = true;
    }

    setState(() {
      _warningState = warningState;
      _visibleWarning = visibleWarning;
      widget.personList = personList;
    });
  }

  Future<Database> createDB() async {
    final database = openDatabase(
      // Set the path to the database. Note: Using the `join` function from the
      // `path` package is best practice to ensure the path is correctly
      // constructed for each platform.
      join(await getDatabasesPath(), 'person.db'),
      // When the database is first created, create a table to store dogs.
      onCreate: (db, version) {
        // Run the CREATE TABLE statement on the database.
        return db.execute(
          'CREATE TABLE person(name text, faceJpg blob, templates blob)',
        );
      },
      // Set the version. This executes the onCreate function and provides a
      // path to perform database upgrades and downgrades.
      version: 1,
    );

    return database;
  }

  // A method that retrieves all the dogs from the dogs table.
  Future<List<Person>> loadAllPersons() async {
    // Get a reference to the database.
    final db = await createDB();

    // Query the table for all The Dogs.
    final List<Map<String, dynamic>> maps = await db.query('person');

    // Convert the List<Map<String, dynamic> into a List<Dog>.
    return List.generate(maps.length, (i) {
      return Person.fromMap(maps[i]);
    });
  }

  Future<void> insertPerson(Person person) async {
    // Get a reference to the database.
    final db = await createDB();

    // Insert the Dog into the correct table. You might also specify the
    // `conflictAlgorithm` to use in case the same dog is inserted twice.
    //
    // In this case, replace any previous data.
    await db.insert(
      'person',
      person.toMap(),
      conflictAlgorithm: ConflictAlgorithm.replace,
    );

    setState(() {
      widget.personList.add(person);
    });
  }

  Future<void> deleteAllPerson() async {
    final db = await createDB();
    await db.delete('person');

    setState(() {
      widget.personList.clear();
    });

    Fluttertoast.showToast(
        msg: "All user deleted!",
        toastLength: Toast.LENGTH_SHORT,
        gravity: ToastGravity.BOTTOM,
        timeInSecForIosWeb: 1,
        backgroundColor: Colors.red,
        textColor: Colors.white,
        fontSize: 16.0);
  }

  Future<void> deletePerson(index) async {
    // ignore: invalid_use_of_protected_member

    final db = await createDB();
    await db.delete('person',
        where: 'name=?', whereArgs: [widget.personList[index].name]);

    // ignore: invalid_use_of_protected_member
    setState(() {
      widget.personList.removeAt(index);
    });

    Fluttertoast.showToast(
        msg: "Person removed!",
        toastLength: Toast.LENGTH_SHORT,
        gravity: ToastGravity.BOTTOM,
        timeInSecForIosWeb: 1,
        backgroundColor: Colors.red,
        textColor: Colors.white,
        fontSize: 16.0);
  }

  Future enrollPerson() async {
    try {
      final image = await ImagePicker().pickImage(source: ImageSource.gallery);
      if (image == null) return;

      var rotatedImage =
          await FlutterExifRotation.rotateImage(path: image.path);

      final faces = await _facesdkPlugin.extractFaces(rotatedImage.path);
      for (var face in faces) {
        num randomNumber =
            10000 + Random().nextInt(10000); // from 0 upto 99 included
        Person person = Person(
            name: 'User$randomNumber',
            faceJpg: face['faceJpg'],
            templates: face['templates']);
        insertPerson(person);
      }

      if (faces.length == 0) {
        Fluttertoast.showToast(
            msg: "No face detected!",
            toastLength: Toast.LENGTH_SHORT,
            gravity: ToastGravity.BOTTOM,
            timeInSecForIosWeb: 1,
            backgroundColor: Colors.red,
            textColor: Colors.white,
            fontSize: 16.0);
      } else {
        Fluttertoast.showToast(
            msg: "User enrolled!",
            toastLength: Toast.LENGTH_SHORT,
            gravity: ToastGravity.BOTTOM,
            timeInSecForIosWeb: 1,
            backgroundColor: Colors.green,
            textColor: Colors.white,
            fontSize: 16.0);
      }
    } catch (e) {}
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: ColorUtils.background1,
      appBar: AppBar(
        elevation: 0,
        title: const Text(
          'Face Recognition',
          style: TextStyle(
            fontSize: 24,
            fontWeight: FontWeight.w700,
            letterSpacing: 0.5,
          ),
        ),
        toolbarHeight: 60,
        centerTitle: true,
        backgroundColor: ColorUtils.background1,
      ),
      body: Padding(
        padding: const EdgeInsets.all(12.0),
        child: Column(
          children: [
            Flexible(
              child: GridView.count(
                shrinkWrap: true,
                crossAxisCount: 2,
                mainAxisSpacing: 10,
                crossAxisSpacing: 10,
                childAspectRatio: 1.1,
                padding: const EdgeInsets.symmetric(vertical: 8),
                children: [
                  _buildGridButton(
                      assetImage: 'assets/add-friend.png',
                      label: 'Enroll',
                      onPressed: enrollPerson,
                      context: context),
                  _buildGridButton(
                      assetImage: 'assets/user.png',
                      label: 'Identify',
                      onPressed: () => _navigateTo(
                          FaceRecognitionView(personList: widget.personList),
                          context),
                      context: context),
                  _buildGridButton(
                      icon: Icons.settings_rounded,
                      label: 'Settings',
                      onPressed: () => _navigateTo(
                          SettingsPage(homePageState: this), context),
                      context: context),
                  _buildGridButton(
                      icon: Icons.info_rounded,
                      label: 'About',
                      onPressed: () => _navigateTo(const AboutPage(), context),
                      context: context),
                ],
              ),
            ),
            const SizedBox(height: 16),
            widget.personList.isEmpty
                ? SizedBox.shrink()
                : Expanded(
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(10),
                      child: Stack(
                        children: [
                          PersonView(
                            personList: widget.personList,
                            homePageState: this,
                          ),
                          _buildWarningOverlay(context),
                        ],
                      ),
                    ),
                  ),
          ],
        ),
      ),
    );
  }

  // Widget _buildGridButton(
  //     {required IconData icon,
  //     required String label,
  //     required VoidCallback onPressed,
  //     required BuildContext context}) {
  //   return SizedBox(
  //     child: Card(
  //       elevation: 2,
  //       shape: RoundedRectangleBorder(
  //         borderRadius: BorderRadius.circular(20),
  //       ),
  //       child: InkWell(
  //         borderRadius: BorderRadius.circular(20),
  //         onTap: onPressed,
  //         splashColor: Theme.of(context).colorScheme.primary.withOpacity(0.1),
  //         highlightColor:
  //             Theme.of(context).colorScheme.secondary.withOpacity(0.05),
  //         child: Container(
  //           decoration: BoxDecoration(
  //             borderRadius: BorderRadius.circular(20),
  //             color: Theme.of(context).primaryColor,
  //           ),
  //           padding: const EdgeInsets.all(16),
  //           child: Column(
  //             mainAxisAlignment: MainAxisAlignment.center,
  //             children: [
  //               Icon(icon,
  //                   size: 60,
  //                   color: Theme.of(context).colorScheme.onPrimaryContainer),
  //               const SizedBox(height: 12),
  //               Text(
  //                 label,
  //                 style: TextStyle(
  //                   fontSize: 16,
  //                   fontWeight: FontWeight.w600,
  //                   color: Theme.of(context).colorScheme.onPrimaryContainer,
  //                   letterSpacing: 0.3,
  //                 ),
  //               ),
  //             ],
  //           ),
  //         ),
  //       ),
  //     ),
  //   );
  // }
  Widget _buildGridButton({
    IconData? icon,
    String? assetImage,
    required String label,
    required VoidCallback onPressed,
    required BuildContext context,
  }) {
    return SizedBox(
      child: Card(
        elevation: 2,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(20),
        ),
        child: InkWell(
          borderRadius: BorderRadius.circular(20),
          onTap: onPressed,
          splashColor: Theme.of(context).colorScheme.primary.withOpacity(0.1),
          highlightColor:
              Theme.of(context).colorScheme.secondary.withOpacity(0.05),
          child: Container(
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(20),
              color: Theme.of(context).primaryColor,
            ),
            padding: const EdgeInsets.all(16),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                if (assetImage != null)
                  Image.asset(
                    assetImage,
                    height: 50,
                    color: Colors.white,
                    alignment: Alignment.center,
                  )
                else if (icon != null)
                  Icon(icon,
                      size: 60,
                      color: Theme.of(context).colorScheme.onPrimaryContainer),
                const SizedBox(height: 12),
                Text(
                  label,
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                    color: Theme.of(context).colorScheme.onPrimaryContainer,
                    letterSpacing: 0.3,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildWarningOverlay(BuildContext context) {
    return Positioned.fill(
      child: Align(
        alignment: Alignment.bottomCenter,
        child: AnimatedSwitcher(
          duration: const Duration(milliseconds: 300),
          transitionBuilder: (child, animation) => FadeTransition(
            opacity: animation,
            child: SizeTransition(
              sizeFactor: animation,
              axisAlignment: -1.0,
              child: child,
            ),
          ),
          child: _visibleWarning
              ? Container(
                  width: double.infinity,
                  margin: const EdgeInsets.all(16),
                  padding:
                      const EdgeInsets.symmetric(vertical: 14, horizontal: 20),
                  decoration: BoxDecoration(
                    color: Theme.of(context).colorScheme.errorContainer,
                    borderRadius: BorderRadius.circular(16),
                    boxShadow: [
                      BoxShadow(
                        color: Theme.of(context)
                            .colorScheme
                            .shadow
                            .withOpacity(0.1),
                        blurRadius: 8,
                        offset: const Offset(0, 2),
                      ),
                    ],
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(Icons.warning_rounded,
                          color:
                              Theme.of(context).colorScheme.onErrorContainer),
                      const SizedBox(width: 12),
                      Flexible(
                        child: Text(
                          _warningState,
                          style: TextStyle(
                            color:
                                Theme.of(context).colorScheme.onErrorContainer,
                            fontSize: 15,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ),
                    ],
                  ),
                )
              : const SizedBox.shrink(),
        ),
      ),
    );
  }

  void _navigateTo(Widget page, BuildContext context) {
    Navigator.push(context, MaterialPageRoute(builder: (context) => page));
  }
}
