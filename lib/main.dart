import 'package:dynamic_color/dynamic_color.dart';
import 'package:flutter/material.dart';

import 'pages/home_page.dart';
import 'package:firebase_core/firebase_core.dart';
import 'firebase_options.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Firebase.initializeApp(
    options: DefaultFirebaseOptions.currentPlatform,
  );
  runApp(const CarLoggerApp());
}

class CarLoggerApp extends StatelessWidget {
  const CarLoggerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return DynamicColorBuilder(
      builder: (ColorScheme? lightDynamic, ColorScheme? darkDynamic) {
        ColorScheme lightColorScheme;
        ColorScheme darkColorScheme;

        if (lightDynamic != null && darkDynamic != null) {
          lightColorScheme = lightDynamic.harmonized();
          darkColorScheme = darkDynamic.harmonized();
        } else {
          lightColorScheme = ColorScheme.fromSeed(
            seedColor: Colors.blue,
            brightness: Brightness.light,
          );
          darkColorScheme = ColorScheme.fromSeed(
            seedColor: Colors.blue,
            brightness: Brightness.dark,
          );
        }

        return MaterialApp(
          title: 'Car Logger',
          theme: ThemeData(
            colorScheme: lightColorScheme,
            useMaterial3: true,
            brightness: Brightness.light,
            cardTheme: const CardThemeData(
              elevation: 4,
              shadowColor: Colors.black45,
            ),
            appBarTheme: const AppBarTheme(
              elevation: 4,
              shadowColor: Colors.black45,
            ),
            navigationBarTheme: const NavigationBarThemeData(
              elevation: 4,
              shadowColor: Colors.black45,
            ),
            floatingActionButtonTheme: const FloatingActionButtonThemeData(
              elevation: 4,
            ),
          ),
          darkTheme: ThemeData(
            colorScheme: darkColorScheme,
            useMaterial3: true,
            brightness: Brightness.dark,
            cardTheme: const CardThemeData(
              elevation: 4,
              shadowColor: Colors.black87,
            ),
            appBarTheme: const AppBarTheme(
              elevation: 4,
              shadowColor: Colors.black87,
            ),
            navigationBarTheme: const NavigationBarThemeData(
              elevation: 4,
              shadowColor: Colors.black87,
            ),
            floatingActionButtonTheme: const FloatingActionButtonThemeData(
              elevation: 4,
            ),
          ),
          themeMode: ThemeMode.system,
          home: const HomePage(),
        );
      },
    );
  }
}
