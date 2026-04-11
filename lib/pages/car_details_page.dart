import 'package:flutter/material.dart';
import '../models/car.dart';
import 'tabs/maintenance_tab.dart';
import 'tabs/tires_tab.dart';
import 'tabs/glovebox_tab.dart';

class CarDetailsPage extends StatefulWidget {
  final Car car;

  const CarDetailsPage({super.key, required this.car});

  @override
  State<CarDetailsPage> createState() => _CarDetailsPageState();
}

class _CarDetailsPageState extends State<CarDetailsPage> {
  int _currentIndex = 0;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('${widget.car.year} ${widget.car.make} ${widget.car.model}'),
      ),
      body: IndexedStack(
        index: _currentIndex,
        children: [
          MaintenanceTab(car: widget.car),
          TiresTab(car: widget.car),
          GloveboxTab(car: widget.car),
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
