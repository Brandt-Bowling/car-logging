import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../models/car.dart';
import '../../models/glovebox.dart';

class GloveboxTab extends StatelessWidget {
  final Car car;
  final ValueChanged<Car>? onCarUpdated;

  const GloveboxTab({
    super.key,
    required this.car,
    this.onCarUpdated,
  });

  Future<void> _editOdometer(BuildContext context) async {
    final controller = TextEditingController(
      text: car.odometer != null ? car.odometer.toString() : '',
    );

    final newOdometer = await showDialog<int>(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: const Text('Edit Odometer'),
          content: TextField(
            controller: controller,
            keyboardType: TextInputType.number,
            decoration: const InputDecoration(
              labelText: 'Odometer (miles)',
              hintText: 'e.g. 45000',
            ),
            autofocus: true,
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () {
                final value = int.tryParse(controller.text);
                if (value != null && value >= 0) {
                  Navigator.pop(context, value);
                } else if (controller.text.isEmpty) {
                  Navigator.pop(context, null);
                }
              },
              child: const Text('Save'),
            ),
          ],
        );
      },
    );

    if (newOdometer != null && newOdometer != car.odometer) {
      onCarUpdated?.call(car.copyWith(odometer: newOdometer));
    }
  }

  @override
  Widget build(BuildContext context) {
    // Mock data
    final glovebox = Glovebox(
      carId: car.id,
      registrationUrl: 'url_to_reg',
      manualUrl: 'url_to_manual',
    );

    return ListView(
      padding: const EdgeInsets.all(16.0),
      children: [
        Card(
          child: ListTile(
            leading: const Icon(Icons.speed),
            title: const Text('Odometer'),
            subtitle: Text(
              car.odometer != null
                  ? '${NumberFormat.decimalPattern().format(car.odometer)} miles'
                  : 'Not set',
            ),
            trailing: IconButton(
              icon: const Icon(Icons.edit),
              onPressed: () => _editOdometer(context),
            ),
          ),
        ),
        const SizedBox(height: 16),
        Text('Documents', style: Theme.of(context).textTheme.titleLarge),
        const SizedBox(height: 8),
        Card(
          child: Column(
            children: [
              ListTile(
                leading: const Icon(Icons.description),
                title: const Text('Registration'),
                subtitle: glovebox.registrationUrl != null ? const Text('Uploaded') : const Text('Not uploaded'),
                trailing: const Icon(Icons.chevron_right),
              ),
              const Divider(height: 1),
              ListTile(
                leading: const Icon(Icons.security),
                title: const Text('Insurance'),
                subtitle: glovebox.insuranceUrl != null ? const Text('Uploaded') : const Text('Not uploaded'),
                trailing: const Icon(Icons.chevron_right),
              ),
              const Divider(height: 1),
              ListTile(
                leading: const Icon(Icons.book),
                title: const Text('Manual'),
                subtitle: glovebox.manualUrl != null ? const Text('Available') : const Text('Not available'),
                trailing: const Icon(Icons.chevron_right),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
