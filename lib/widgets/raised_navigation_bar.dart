import 'package:flutter/material.dart';

/// A Material 3 navigation bar with a prominent upward elevation shadow
/// and subtle top border line, providing a visibly raised appearance
/// similar to the docked navigation bar in Gmail on Android.
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
    final isDark = theme.brightness == Brightness.dark;

    return Container(
      decoration: BoxDecoration(
        color: colorScheme.surfaceContainer,
        border: Border(
          top: BorderSide(
            color: colorScheme.outlineVariant.withValues(alpha: 0.35),
            width: 0.5,
          ),
        ),
        boxShadow: [
          BoxShadow(
            color: isDark
                ? Colors.black.withValues(alpha: 0.5)
                : Colors.black.withValues(alpha: 0.10),
            blurRadius: 10,
            spreadRadius: 0,
            offset: const Offset(0, -3),
          ),
        ],
      ),
      child: NavigationBar(
        selectedIndex: selectedIndex,
        destinations: destinations,
        onDestinationSelected: onDestinationSelected,
        height: height,
        labelBehavior: labelBehavior,
        animationDuration: animationDuration,
        backgroundColor: Colors.transparent,
        elevation: 0,
      ),
    );
  }
}
