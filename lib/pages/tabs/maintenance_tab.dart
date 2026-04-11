import 'package:flutter/material.dart';
import '../../models/car.dart';
import '../../models/maintenance_record.dart';

class MaintenanceTab extends StatelessWidget {
  final Car car;

  const MaintenanceTab({super.key, required this.car});

  @override
  Widget build(BuildContext context) {
    // Mock data for display
    final records = [
      MaintenanceRecord(
        id: '1',
        carId: car.id,
        title: 'Oil Change',
        date: DateTime.now().subtract(const Duration(days: 30)),
        odometer: 45000,
        cost: 65.0,
      ),
    ];

    return ListView(
      padding: const EdgeInsets.all(16.0),
      children: [
        Card(
          color: Theme.of(context).colorScheme.secondaryContainer,
          child: const ListTile(
            leading: Icon(Icons.calendar_month),
            title: Text('Next Scheduled Maintenance'),
            subtitle: Text('Oil Change at 50,000 miles'),
          ),
        ),
        const SizedBox(height: 16),
        Text(
          'History',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 8),
        ...records.map((record) => Card(
              child: ListTile(
                title: Text(record.title),
                subtitle: Text('${record.date.toLocal().toString().split(' ')[0]} - ${record.odometer} mi'),
                trailing: record.cost != null ? Text('\$${record.cost}') : null,
              ),
            )),
      ],
    );
  }
}
