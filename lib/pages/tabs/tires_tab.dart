import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../models/car.dart';
import '../../models/tire.dart';
import '../../services/storage_service.dart';
import '../../widgets/tire_rotation_interval_dialog.dart';

class TiresTab extends StatelessWidget {
  final Car car;
  final ValueChanged<Car>? onCarUpdated;

  const TiresTab({super.key, required this.car, this.onCarUpdated});

  Future<void> _editRotationInterval(BuildContext context) async {
    final newInterval = await showTireRotationIntervalDialog(
      context,
      currentInterval: car.effectiveTireRotationInterval,
    );
    if (newInterval != null && newInterval != car.tireRotationInterval) {
      final updatedCar = car.copyWith(tireRotationInterval: newInterval);
      await StorageService.updateCar(updatedCar);
      onCarUpdated?.call(updatedCar);
    }
  }

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
      padding: const EdgeInsets.fromLTRB(16.0, 20.0, 16.0, 80.0),
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
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Rotation Interval'),
                  subtitle: const Text('Recommended interval between rotations'),
                  trailing: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        '${NumberFormat.decimalPattern().format(car.effectiveTireRotationInterval)} miles',
                        style: const TextStyle(fontWeight: FontWeight.bold),
                      ),
                      const SizedBox(width: 8),
                      const Icon(Icons.edit, size: 18),
                    ],
                  ),
                  onTap: () => _editRotationInterval(context),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
