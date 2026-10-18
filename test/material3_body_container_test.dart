import 'package:car_logger/widgets/material3_body_container.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('Material3ContainerClipper shapes top corners correctly and contains body', () {
    const clipper = Material3ContainerClipper(radius: 28.0);
    const size = Size(400, 800);
    final path = clipper.getClip(size);

    // Center of body should definitely be contained
    expect(path.contains(const Offset(200, 400)), isTrue);

    // Left and right edges along body should be fully contained (due to overflow extension)
    expect(path.contains(const Offset(0, 100)), isTrue);
    expect(path.contains(const Offset(400, 100)), isTrue);

    // Near the bottom
    expect(path.contains(const Offset(0, 800)), isTrue);
    expect(path.contains(const Offset(400, 800)), isTrue);

    // The top-left corner cutout outside the 28dp radius must NOT be contained
    expect(path.contains(const Offset(0, 0)), isFalse);
    expect(path.contains(const Offset(5, 5)), isFalse);

    // The top-right corner cutout outside the 28dp radius must NOT be contained
    expect(path.contains(const Offset(400, 0)), isFalse);
    expect(path.contains(const Offset(395, 5)), isFalse);

    // Top center edge should be contained
    expect(path.contains(const Offset(200, 1)), isTrue);
  });

  testWidgets('Material3BodyContainer renders child on solid surface color', (tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: Material3BodyContainer(
            child: Text('Content inside container'),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Content inside container'), findsOneWidget);
    expect(find.byType(Material3BodyContainer), findsOneWidget);
    expect(find.byType(ClipPath), findsOneWidget);
    expect(
      find.descendant(
        of: find.byType(Material3BodyContainer),
        matching: find.byType(ColoredBox),
      ),
      findsOneWidget,
    );
  });
}
