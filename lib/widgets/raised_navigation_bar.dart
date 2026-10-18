import 'package:flutter/material.dart';

/// A Material 3 navigation bar with solid color transitions and zero shadows,
/// matching Google apps like Gmail on Android.
///
/// Transitions cleanly between the body canvas and docked navigation bar
/// using the standard Material 3 [ColorScheme.surfaceContainer] tone.
class RaisedNavigationBar extends StatelessWidget {
  const RaisedNavigationBar({
    super.key,
    required this.selectedIndex,
    required this.destinations,
    this.onDestinationSelected,
    this.height,
    this.labelBehavior,
    this.animationDuration,
  });

  final int selectedIndex;
  final List<Widget> destinations;
  final ValueChanged<int>? onDestinationSelected;
  final double? height;
  final NavigationDestinationLabelBehavior? labelBehavior;
  final Duration? animationDuration;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;

    return Container(
      color: colorScheme.surfaceContainer,
      child: NavigationBar(
        selectedIndex: selectedIndex,
        destinations: destinations,
        onDestinationSelected: onDestinationSelected,
        height: height,
        labelBehavior: labelBehavior,
        animationDuration: animationDuration,
        backgroundColor: colorScheme.surfaceContainer,
        elevation: 0,
        shadowColor: Colors.transparent,
      ),
    );
  }
}
