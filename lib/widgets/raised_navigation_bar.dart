import 'package:flutter/material.dart';
import 'material3_body_container.dart';

/// A Material 3 navigation bar with solid color transitions and zero shadows,
/// matching Google apps like Gmail on Android.
///
/// Transitions cleanly between the body canvas and docked navigation bar
/// using the standard Material 3 shell container tone matching the header.
class RaisedNavigationBar extends StatelessWidget {
  const RaisedNavigationBar({
    super.key,
    required this.selectedIndex,
    required this.destinations,
    this.onDestinationSelected,
    this.height,
    this.labelBehavior,
    this.animationDuration,
    this.backgroundColor,
  });

  final int selectedIndex;
  final List<Widget> destinations;
  final ValueChanged<int>? onDestinationSelected;
  final double? height;
  final NavigationDestinationLabelBehavior? labelBehavior;
  final Duration? animationDuration;
  final Color? backgroundColor;

  @override
  Widget build(BuildContext context) {
    final shellColor = backgroundColor ?? Material3BodyContainer.getShellColor(context);

    return Container(
      color: shellColor,
      child: NavigationBar(
        selectedIndex: selectedIndex,
        destinations: destinations,
        onDestinationSelected: onDestinationSelected,
        height: height,
        labelBehavior: labelBehavior,
        animationDuration: animationDuration,
        backgroundColor: shellColor,
        elevation: 0,
        shadowColor: Colors.transparent,
      ),
    );
  }
}
