import 'package:flutter/material.dart';
import '../../models/car.dart';
import '../../models/glovebox.dart';

class GloveboxTab extends StatelessWidget {
  final Car car;

  const GloveboxTab({super.key, required this.car});

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
            subtitle: const Text('45,000 miles'),
            trailing: IconButton(
              icon: const Icon(Icons.edit),
              onPressed: () {},
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
