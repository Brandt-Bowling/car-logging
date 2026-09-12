import 'package:flutter/material.dart';
import '../models/car.dart';
import '../widgets/maintenance_record_dialog.dart';
import 'tabs/maintenance_tab.dart';
import 'tabs/tires_tab.dart';
import 'tabs/glovebox_tab.dart';

class CarDetailsPage extends StatefulWidget {
  final Car car;
  final ValueChanged<Car>? onCarUpdated;

  const CarDetailsPage({super.key, required this.car, this.onCarUpdated});

  @override
  State<CarDetailsPage> createState() => _CarDetailsPageState();
}

class _CarDetailsPageState extends State<CarDetailsPage> {
  int _currentIndex = 0;
  late Car _car;
  final _maintenanceTabKey = GlobalKey<MaintenanceTabState>();

  @override
  void initState() {
    super.initState();
    _car = widget.car;
  }

  void _onCarUpdated(Car updatedCar) {
    setState(() {
      _car = updatedCar;
    });
    widget.onCarUpdated?.call(updatedCar);
  }

  Future<void> _addMaintenanceRecord() async {
    final result = await showMaintenanceRecordDialog(
      context,
      car: _car,
      onCarUpdated: _onCarUpdated,
    );

    if (result != null && mounted) {
      _maintenanceTabKey.currentState?.loadRecords();
      if (result.action == MaintenanceRecordAction.saved) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Added "${result.record?.title ?? 'Record'}"'),
            behavior: SnackBarBehavior.floating,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('${_car.year} ${_car.make} ${_car.model}'),
      ),
      body: IndexedStack(
        index: _currentIndex,
        children: [
          MaintenanceTab(
            key: _maintenanceTabKey,
            car: _car,
            onCarUpdated: _onCarUpdated,
          ),
          TiresTab(car: _car),
          GloveboxTab(
            car: _car,
            onCarUpdated: _onCarUpdated,
          ),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _currentIndex,
        onDestinationSelected: (index) {
          setState(() {
            _currentIndex = index;
          });
        },
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.build),
            label: 'Maintenance',
          ),
          NavigationDestination(
            icon: Icon(Icons.tire_repair),
            label: 'Tires',
          ),
          NavigationDestination(
            icon: Icon(Icons.inventory_2),
            label: 'Glovebox',
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () {
          if (_currentIndex == 0) {
            _addMaintenanceRecord();
          }
        },
        child: _getFabIcon(),
      ),
    );
  }

  Widget _getFabIcon() {
    switch (_currentIndex) {
      case 0:
        return const Icon(Icons.add); // Add maintenance
      case 1:
        return const Icon(Icons.add); // Add tire record
      case 2:
        return const Icon(Icons.upload_file); // Upload doc
      default:
        return const Icon(Icons.add);
    }
  }
}
