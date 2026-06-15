import 'package:flutter/material.dart';
import '../models/car.dart';
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

  @override
  void initState() {
    super.initState();
    _car = widget.car;
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
          MaintenanceTab(car: _car),
          TiresTab(car: _car),
          GloveboxTab(
            car: _car,
            onCarUpdated: (updatedCar) {
              setState(() {
                _car = updatedCar;
              });
              widget.onCarUpdated?.call(updatedCar);
            },
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
          // Action depends on _currentIndex
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
