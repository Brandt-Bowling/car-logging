import 'package:flutter/material.dart';
import '../../models/car.dart';
import '../../models/tire.dart';

class TiresTab extends StatelessWidget {
  final Car car;

  const TiresTab({super.key, required this.car});

  @override
  Widget build(BuildContext context) {
    // Mock data
    final tire = Tire(
      id: '1',
      carId: car.id,
      manufacturer: 'Michelin',
      model: 'Defender',
      dateInstalled: DateTime.now().subtract(const Duration(days: 365)),
      odometerInstalled: 40000,
      estimatedTreadLifeMiles: 60000,
    );

    return ListView(
      padding: const EdgeInsets.all(16.0),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Current Set', style: Theme.of(context).textTheme.titleMedium),
                const Divider(),
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Manufacturer'),
                  trailing: Text(tire.manufacturer),
                ),
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Date Installed'),
                  trailing: Text(tire.dateInstalled.toLocal().toString().split(' ')[0]),
                ),
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Tread Life'),
                  trailing: Text('${tire.estimatedTreadLifeMiles} miles'),
                ),
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Last Rotation'),
                  trailing: const Text('Not recorded'),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
