import 'package:flutter/material.dart';

/// Clipper for Material 3 rounded body container canvas.
///
/// In Material 3 container-based layouts (such as Gmail), a sheet/canvas with
/// rounded top corners (typically 28dp radius) rests on top of the
/// `surfaceContainer` header canvas.
///
/// To completely eliminate anti-aliasing artifacts (such as 1px vertical bleed
/// lines along the left and right display edges where the underlying background
/// shows through), this clipper extends its straight vertical and bottom edges
/// off-screen. Only the top-left and top-right corner arcs and the top horizontal
/// edge lie on the visible screen boundary.
class Material3ContainerClipper extends CustomClipper<Path> {
  const Material3ContainerClipper({this.radius = 28.0});

  final double radius;

  @override
  Path getClip(Size size) {
    const double overflow = 16.0;
    final path = Path();

    // Start off-screen on the left margin, at the level where the corner begins
    path.moveTo(-overflow, radius);

    // Enter screen at left edge where the top-left corner curve starts
    path.lineTo(0, radius);

    // Top-left rounded corner (curves up and right to the top edge)
    path.arcToPoint(
      Offset(radius, 0),
      radius: Radius.circular(radius),
      clockwise: true,
    );

    // Top horizontal edge
    path.lineTo(size.width - radius, 0);

    // Top-right rounded corner (curves right and down to the right edge)
    path.arcToPoint(
      Offset(size.width, radius),
      radius: Radius.circular(radius),
      clockwise: true,
    );

    // Exit off-screen on the right margin
    path.lineTo(size.width + overflow, radius);

    // Off-screen right edge down to bottom
    path.lineTo(size.width + overflow, size.height + overflow);

    // Off-screen bottom edge to left
    path.lineTo(-overflow, size.height + overflow);

    path.close();
    return path;
  }

  @override
  bool shouldReclip(covariant Material3ContainerClipper oldClipper) =>
      oldClipper.radius != radius;
}

/// A container implementing the Material 3 Expressive enveloped body canvas
/// (like in Gmail for Android) with solid color transitions and zero edge bleeding.
class Material3BodyContainer extends StatelessWidget {
  const Material3BodyContainer({
    super.key,
    required this.child,
    this.topRadius = 28.0,
    this.color,
  });

  final Widget child;
  final double topRadius;
  final Color? color;

  /// Gets the standard Material 3 enveloped body canvas color for the given context.
  ///
  /// In M3 container-based layouts (like Gmail), the body canvas is a distinct
  /// sheet layer resting on top of the outer shell (header and navigation bar).
  ///
  /// In light mode, this is crisp pure white [Colors.white] / [ColorScheme.surfaceContainerLowest].
  /// In dark mode, this is a deep dark canvas tone (#101216) that provides prominent contrast
  /// against the elevated outer shell.
  static Color getCanvasColor(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final colorScheme = theme.colorScheme;

    if (isDark) {
      return Color.alphaBlend(
        colorScheme.surfaceContainerLowest,
        const Color(0xFF101216),
      );
    } else {
      return Colors.white;
    }
  }

  /// Gets the standard Material 3 outer shell background color (for Header & Navigation Bar).
  ///
  /// This provides the contrasting backdrop behind the body canvas sheet,
  /// matching the top app bar / header area and the bottom navigation bar.
  ///
  /// In light mode, this is a soft tinted container (#E9ECF2 / surfaceContainerHigh).
  /// In dark mode, this is a prominent elevated surface container (#282B32 / surfaceContainerHighest).
  static Color getShellColor(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final colorScheme = theme.colorScheme;

    if (isDark) {
      return Color.alphaBlend(
        colorScheme.primary.withValues(alpha: 0.08),
        Color.alphaBlend(colorScheme.surfaceContainerHighest, const Color(0xFF262930)),
      );
    } else {
      return Color.alphaBlend(
        colorScheme.primary.withValues(alpha: 0.05),
        Color.alphaBlend(colorScheme.surfaceContainerHigh, const Color(0xFFE9ECF2)),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final surfaceColor = color ?? getCanvasColor(context);

    return ClipPath(
      clipper: Material3ContainerClipper(radius: topRadius),
      child: ColoredBox(
        color: surfaceColor,
        child: child,
      ),
    );
  }
}
