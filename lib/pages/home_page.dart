import 'package:flutter/material.dart';
import '../models/car.dart';
import '../widgets/add_car_modal.dart';
import 'car_details_page.dart';
import '../services/storage_service.dart';
import 'google_drive_sync_page.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  List<Car> _cars = [];

  @override
  void initState() {
    super.initState();
    _loadCars();
  }

  void _loadCars() {
    setState(() {
      _cars = StorageService.getCars();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('My Cars'),
        actions: [
          IconButton(
            icon: const Icon(Icons.cloud_sync),
            tooltip: 'Sync Google Drive',
            onPressed: () async {
              await Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (context) => const GoogleDriveSyncPage(),
                ),
              );
              _loadCars();
            },
          ),
        ],
      ),
      body: _cars.isEmpty
          ? const Center(child: Text('No cars added yet.'))
          : ListView.builder(
              itemCount: _cars.length,
              itemBuilder: (context, index) {
                final car = _cars[index];
                return Card(
                  margin: const EdgeInsets.all(8.0),
                  child: InkWell(
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(
                          builder: (context) => CarDetailsPage(
                            car: car,
                            onCarUpdated: (updatedCar) {
                              setState(() {
                                final carIndex = _cars.indexWhere((c) => c.id == updatedCar.id);
                                if (carIndex != -1) {
                                  _cars[carIndex] = updatedCar;
                                }
                              });
                            },
                          ),
                        ),
                      );
                    },
                    child: Padding(
                      padding: const EdgeInsets.all(16.0),
                      child: Row(
                        children: [
                          Container(
                            width: 80,
                            height: 80,
                            decoration: BoxDecoration(
                              color: Theme.of(context).colorScheme.primaryContainer,
                              borderRadius: BorderRadius.circular(8.0),
                            ),
                            child: const Icon(Icons.directions_car, size: 40),
                          ),
                          const SizedBox(width: 16),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  '${car.year} ${car.make} ${car.model}',
                                  style: Theme.of(context).textTheme.titleLarge,
                                ),
                                if (car.licensePlate != null)
                                  Text(
                                    car.licensePlate!,
                                    style: Theme.of(context).textTheme.bodyMedium,
                                  ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                );
              },
            ),
      floatingActionButton: FloatingActionButton(
        onPressed: () async {
          final newCar = await showModalBottomSheet<Car>(
            context: context,
            isScrollControlled: true,
            backgroundColor: Colors.transparent,
            builder: (context) => const AddCarModal(),
          );

          if (newCar != null) {
            await StorageService.addCar(newCar);
            _loadCars();

            if (!context.mounted) return;

            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text('Added ${newCar.make} ${newCar.model}'),
                behavior: SnackBarBehavior.floating,
              ),
            );
          }
        },
        child: const Icon(Icons.add),
      ),
    );
  }
}
