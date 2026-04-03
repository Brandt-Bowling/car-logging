import 'package:flutter/material.dart';
import '../models/car.dart';
import 'car_details_page.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  final List<Car> _cars = [
    Car(
      id: '1',
      make: 'Toyota',
      model: 'Camry',
      year: 2020,
      licensePlate: 'ABC-1234',
    ),
    Car(
      id: '2',
      make: 'Honda',
      model: 'Civic',
      year: 2018,
      licensePlate: 'XYZ-9876',
    ),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('My Cars'),
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
                          builder: (context) => CarDetailsPage(car: car),
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
        onPressed: () {
          // TODO: Implement add car
        },
        child: const Icon(Icons.add),
      ),
    );
  }
}
