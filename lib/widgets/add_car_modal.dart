import 'dart:io' show File;

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:image_picker/image_picker.dart';
import 'package:uuid/uuid.dart';

import '../data/vehicle_data.dart';
import '../models/car.dart';
import '../services/auto_dev_service.dart';
import '../services/storage_service.dart';

class AddCarModal extends StatefulWidget {
  final bool openVinPrompt;
  final String? initialVin;

  const AddCarModal({
    super.key,
    this.openVinPrompt = false,
    this.initialVin,
  });

  @override
  State<AddCarModal> createState() => _AddCarModalState();
}

class _AddCarModalState extends State<AddCarModal> {
  final GlobalKey<ScaffoldMessengerState> _scaffoldMessengerKey =
      GlobalKey<ScaffoldMessengerState>();
  final _formKey = GlobalKey<FormState>();

  void _showSnackBar(SnackBar snackBar) {
    if (!mounted) return;
    final messenger = _scaffoldMessengerKey.currentState;
    if (messenger != null) {
      messenger.removeCurrentSnackBar();
      messenger.showSnackBar(snackBar);
    } else {
      ScaffoldMessenger.of(context).removeCurrentSnackBar();
      ScaffoldMessenger.of(context).showSnackBar(snackBar);
    }
  }

  // Make / Model selection
  String? _selectedMake;
  String? _selectedModel;
  final _makeController = TextEditingController();
  final _modelController = TextEditingController();
  final _makeFocusNode = FocusNode();
  final _modelFocusNode = FocusNode();

  // VIN & AutoDev
  final _vinController = TextEditingController();
  final _vinFocusNode = FocusNode();
  bool _isLoadingVin = false;
  String? _vinErrorMessage;
  bool _isApiKeyError = false;
  String? _decodedTrim;
  String? _decodedBodyStyle;

  // Other fields
  int _selectedYear = 2010;
  String? _imageUrl;
  String? _localImagePath;
  List<String> _availableImageUrls = [];
  int _selectedImageIndex = 0;
  late final PageController _imagePageController;
  String? _licensePlate;
  String? _vin;
  int? _odometer;
  bool _isEv = false;

  // Image picker
  final ImagePicker _picker = ImagePicker();

  // Validation helpers
  bool _makeError = false;
  bool _modelError = false;

  DraggableScrollableController? _sheetController;

  @override
  void initState() {
    super.initState();
    _sheetController = DraggableScrollableController();
    _imagePageController = PageController();

    if (widget.initialVin != null && widget.initialVin!.isNotEmpty) {
      _vin = widget.initialVin;
      _vinController.text = widget.initialVin!;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) {
          _decodeVin(widget.initialVin!);
        }
      });
    } else if (widget.openVinPrompt) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) {
          _promptVinLookup();
        }
      });
    }
  }

  @override
  void dispose() {
    _makeController.dispose();
    _modelController.dispose();
    _makeFocusNode.dispose();
    _modelFocusNode.dispose();
    _vinController.dispose();
    _vinFocusNode.dispose();
    _sheetController?.dispose();
    _imagePageController.dispose();
    super.dispose();
  }

  void _expandSheetIfKeyboardOpen() {
    if (!mounted) return;
    final bottomInset = MediaQuery.of(context).viewInsets.bottom;
    if (bottomInset > 0 && _sheetController != null && _sheetController!.isAttached) {
      if (_sheetController!.size < 0.9) {
        _sheetController!.animateTo(
          0.95,
          duration: const Duration(milliseconds: 200),
          curve: Curves.easeOut,
        );
      }
    }
  }

  EdgeInsets _getScrollPadding(BuildContext context) {
    final bottomInset = MediaQuery.of(context).viewInsets.bottom;
    return EdgeInsets.only(bottom: bottomInset + 100.0);
  }

  // ---------------------------------------------------------------------------
  // Selection handlers
  // ---------------------------------------------------------------------------

  void _onMakeSelected(String make) {
    setState(() {
      _selectedMake = make;
      _selectedModel = null;
      _modelController.clear();
      _imageUrl = null;
      _localImagePath = null;
      _availableImageUrls = [];
      _selectedImageIndex = 0;
      _makeError = false;
      // Don't auto-reset EV — user may have toggled it manually
    });
  }

  void _onModelSelected(String model) {
    setState(() {
      _selectedModel = model;
      _modelError = false;

      // Auto-populate default image (if user hasn't picked a custom one)
      if (_selectedMake != null && _localImagePath == null) {
        final defaultUrl = getDefaultImageUrl(_selectedMake!, model);
        _imageUrl = defaultUrl;
        _availableImageUrls = defaultUrl != null ? [defaultUrl] : [];
        _selectedImageIndex = 0;
      }

      // Auto-detect EV
      if (_selectedMake != null && isKnownEv(_selectedMake!, model)) {
        _isEv = true;
      }
    });
  }

  // ---------------------------------------------------------------------------
  // Image actions
  // ---------------------------------------------------------------------------

  Future<void> _pickImage() async {
    final XFile? image = await _picker.pickImage(
      source: ImageSource.gallery,
      maxWidth: 1200,
      maxHeight: 800,
      imageQuality: 85,
    );
    if (image != null) {
      setState(() {
        _localImagePath = image.path;
        _imageUrl = null;
      });
    }
  }

  void _useDefaultImage() {
    if (_selectedMake != null && _selectedModel != null) {
      final url = getDefaultImageUrl(_selectedMake!, _selectedModel!);
      if (url != null) {
        setState(() {
          _imageUrl = url;
          _localImagePath = null;
          if (!_availableImageUrls.contains(url)) {
            _availableImageUrls.add(url);
          }
          _selectedImageIndex = _availableImageUrls.indexOf(url);
        });
        if (_imagePageController.hasClients) {
          _imagePageController.animateToPage(
            _selectedImageIndex,
            duration: const Duration(milliseconds: 300),
            curve: Curves.easeInOut,
          );
        }
      } else {
        if (mounted) {
          _showSnackBar(
            const SnackBar(
              content: Text('No default image available for this model'),
              behavior: SnackBarBehavior.floating,
            ),
          );
        }
      }
    }
  }

  void _removeImage() {
    setState(() {
      _imageUrl = null;
      _localImagePath = null;
    });
  }

  // ---------------------------------------------------------------------------
  // VIN actions
  // ---------------------------------------------------------------------------

  void _applyDecodedInfo(AutoDevVehicleInfo info, String cleanVin) {
    setState(() {
      _vin = cleanVin;
      _vinController.text = cleanVin;
      _decodedTrim = info.trim;
      _decodedBodyStyle = info.body;
      _vinErrorMessage = null;
      _isApiKeyError = false;

      // Populate Make
      _selectedMake = info.make;
      _makeError = false;

      // Populate Model - smart match with known models if possible
      final knownModels = getModelsForMake(info.make!);
      String resolvedModel = info.model ?? '';
      if (info.model != null && knownModels.isNotEmpty) {
        final exactMatch = knownModels.firstWhere(
          (m) => m.toLowerCase() == info.model!.toLowerCase(),
          orElse: () => '',
        );
        if (exactMatch.isNotEmpty) {
          resolvedModel = exactMatch;
        } else {
          final partialMatch = knownModels.firstWhere(
            (m) => info.model!.toLowerCase().contains(m.toLowerCase()),
            orElse: () => '',
          );
          if (partialMatch.isNotEmpty) {
            resolvedModel = partialMatch;
          }
        }
      }
      _selectedModel = resolvedModel.isNotEmpty ? resolvedModel : info.model;
      _modelError = false;

      // Populate Year
      if (info.year != null) {
        _selectedYear = info.year!;
      }

      // Populate EV
      _isEv = info.isEv;

      // Populate Photos (from auto-dev)
      _availableImageUrls = List<String>.from(info.photoUrls);
      if (info.photoUrl != null &&
          info.photoUrl!.isNotEmpty &&
          !_availableImageUrls.contains(info.photoUrl!)) {
        _availableImageUrls.insert(0, info.photoUrl!);
      }
      _selectedImageIndex = 0;
      if (_availableImageUrls.isNotEmpty) {
        _imageUrl = _availableImageUrls.first;
        _localImagePath = null;
      } else if (info.photoUrl != null && info.photoUrl!.isNotEmpty) {
        _imageUrl = info.photoUrl;
        _localImagePath = null;
      }
    });

    if (_imagePageController.hasClients) {
      _imagePageController.jumpToPage(0);
    }

    if (mounted) {
      _showSnackBar(
        SnackBar(
          content: Text(
            'Vehicle details populated for $_selectedYear ${_selectedMake ?? ''} ${_selectedModel ?? ''}!',
          ),
          behavior: SnackBarBehavior.floating,
        ),
      );
    }
  }

  Future<void> _decodeVin(String rawVin) async {
    final cleanVin = rawVin.trim().toUpperCase();
    if (cleanVin.isEmpty) {
      setState(() {
        _vinErrorMessage = 'Please enter a VIN to decode';
        _isApiKeyError = false;
      });
      if (mounted) {
        _showSnackBar(
          const SnackBar(
            content: Text('Please enter a VIN to decode'),
            behavior: SnackBarBehavior.floating,
          ),
        );
      }
      return;
    }

    setState(() {
      _isLoadingVin = true;
      _vinErrorMessage = null;
      _isApiKeyError = false;
    });

    try {
      final info = await AutoDevService.decodeAndFetchDetails(cleanVin);

      if (!mounted) return;

      if (!info.vinValid || info.make == null) {
        final error = info.errorMessage ?? 'Could not decode vehicle details for VIN "$cleanVin".';
        final isAuth = error.toLowerCase().contains('api key') ||
            error.toLowerCase().contains('401') ||
            error.toLowerCase().contains('authentication') ||
            error.toLowerCase().contains('unauthorized');
        setState(() {
          _vinErrorMessage = error;
          _isApiKeyError = isAuth;
          _isLoadingVin = false;
        });
        _showSnackBar(
          SnackBar(
            content: Text(error),
            backgroundColor: Theme.of(context).colorScheme.error,
            behavior: SnackBarBehavior.floating,
          ),
        );
        return;
      }

      _applyDecodedInfo(info, cleanVin);
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _vinErrorMessage = 'Failed to decode VIN: $e';
        _isApiKeyError = false;
      });
      _showSnackBar(
        SnackBar(
          content: Text('Failed to decode VIN: $e'),
          backgroundColor: Theme.of(context).colorScheme.error,
          behavior: SnackBarBehavior.floating,
        ),
      );
    } finally {
      if (mounted) {
        setState(() {
          _isLoadingVin = false;
        });
      }
    }
  }

  void _promptVinLookup() {
    final dialogController = TextEditingController(text: _vinController.text);
    bool isDialogLoading = false;
    String? dialogError;
    bool isDialogApiKeyError = false;

    showDialog(
      context: context,
      builder: (dialogContext) => StatefulBuilder(
        builder: (context, setDialogState) {
          Future<void> performDialogDecode(String rawVin) async {
            final vin = rawVin.trim().toUpperCase();
            if (vin.isEmpty) {
              setDialogState(() {
                dialogError = 'Please enter a VIN';
                isDialogApiKeyError = false;
              });
              return;
            }
            setDialogState(() {
              isDialogLoading = true;
              dialogError = null;
              isDialogApiKeyError = false;
            });

            try {
              final info = await AutoDevService.decodeAndFetchDetails(vin);
              if (!dialogContext.mounted) return;

              if (!info.vinValid || info.make == null) {
                final err = info.errorMessage ?? 'Could not decode vehicle details for VIN "$vin".';
                final isAuth = err.toLowerCase().contains('api key') ||
                    err.toLowerCase().contains('401') ||
                    err.toLowerCase().contains('authentication') ||
                    err.toLowerCase().contains('unauthorized');
                setDialogState(() {
                  isDialogLoading = false;
                  dialogError = err;
                  isDialogApiKeyError = isAuth;
                });
                return;
              }

              // Success! Pop dialog and apply to modal
              Navigator.pop(dialogContext);
              _applyDecodedInfo(info, vin);
            } catch (e) {
              if (dialogContext.mounted) {
                setDialogState(() {
                  isDialogLoading = false;
                  dialogError = 'Failed to decode VIN: $e';
                  isDialogApiKeyError = false;
                });
              }
            }
          }

          return AlertDialog(
            title: const Row(
              children: [
                Icon(Icons.auto_awesome),
                SizedBox(width: 8),
                Text('Add Vehicle by VIN'),
              ],
            ),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Enter your 17-character VIN. Vehicle make, model, year, and photo will be automatically retrieved via auto.dev.',
                  style: TextStyle(fontSize: 13),
                ),
                const SizedBox(height: 16),
                TextField(
                  controller: dialogController,
                  autofocus: true,
                  enabled: !isDialogLoading,
                  textCapitalization: TextCapitalization.characters,
                  decoration: InputDecoration(
                    labelText: 'Vehicle Identification Number',
                    hintText: 'e.g., 1HGCR2F83HA000000',
                    border: const OutlineInputBorder(),
                    prefixIcon: const Icon(Icons.pin_outlined),
                    suffixIcon: dialogController.text.isNotEmpty && !isDialogLoading
                        ? IconButton(
                            icon: const Icon(Icons.clear, size: 18),
                            onPressed: () {
                              setDialogState(() {
                                dialogController.clear();
                                dialogError = null;
                                isDialogApiKeyError = false;
                              });
                            },
                          )
                        : null,
                  ),
                  onChanged: (_) {
                    if (dialogError != null) {
                      setDialogState(() {
                        dialogError = null;
                        isDialogApiKeyError = false;
                      });
                    }
                  },
                  onSubmitted: (value) => performDialogDecode(value),
                ),
                if (dialogError != null) ...[
                  const SizedBox(height: 12),
                  Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: Theme.of(context).colorScheme.errorContainer,
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(
                        color: Theme.of(context).colorScheme.error.withValues(alpha: 0.3),
                      ),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Icon(Icons.error_outline,
                                size: 18,
                                color: Theme.of(context).colorScheme.error),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Text(
                                dialogError!,
                                style: TextStyle(
                                  fontSize: 12,
                                  color: Theme.of(context).colorScheme.onErrorContainer,
                                  fontWeight: FontWeight.w500,
                                ),
                              ),
                            ),
                          ],
                        ),
                        if (isDialogApiKeyError) ...[
                          const SizedBox(height: 8),
                          Align(
                            alignment: Alignment.centerRight,
                            child: TextButton.icon(
                              onPressed: () async {
                                final vin = dialogController.text.trim();
                                await _showApiKeyDialog(triggerDecode: false);
                                if (dialogContext.mounted) {
                                  if (vin.isNotEmpty) {
                                    performDialogDecode(vin);
                                  } else {
                                    setDialogState(() {
                                      dialogError = null;
                                      isDialogApiKeyError = false;
                                    });
                                  }
                                }
                              },
                              icon: const Icon(Icons.key, size: 14),
                              label: const Text('Set API Key'),
                              style: TextButton.styleFrom(
                                visualDensity: VisualDensity.compact,
                              ),
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                ],
              ],
            ),
            actions: [
              TextButton(
                onPressed: isDialogLoading ? null : () => Navigator.pop(dialogContext),
                child: const Text('Cancel'),
              ),
              FilledButton.icon(
                onPressed: isDialogLoading
                    ? null
                    : () => performDialogDecode(dialogController.text),
                icon: isDialogLoading
                    ? const SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                      )
                    : const Icon(Icons.auto_awesome, size: 18),
                label: Text(isDialogLoading ? 'Decoding…' : 'Decode & Auto-fill'),
              ),
            ],
          );
        },
      ),
    );
  }

  Future<void> _showApiKeyDialog({bool triggerDecode = true}) async {
    final currentKey = StorageService.getAutoDevApiKey() ?? '';
    final controller = TextEditingController(text: currentKey);

    final saved = await showDialog<bool>(
      context: context,
      builder: (dialogCtx) => AlertDialog(
        title: const Row(
          children: [
            Icon(Icons.key),
            SizedBox(width: 8),
            Text('auto.dev API Key'),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Enter your auto.dev API key from https://auto.dev/dashboard/api-keys to decode VINs and fetch retail vehicle photos.',
              style: TextStyle(fontSize: 13),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: controller,
              autofocus: true,
              decoration: const InputDecoration(
                labelText: 'API Key',
                hintText: 'Enter API key or Bearer token',
                border: OutlineInputBorder(),
                prefixIcon: Icon(Icons.vpn_key_outlined),
              ),
            ),
          ],
        ),
        actions: [
          if (currentKey.isNotEmpty)
            TextButton(
              onPressed: () async {
                await StorageService.saveAutoDevApiKey('');
                if (dialogCtx.mounted) Navigator.pop(dialogCtx, true);
              },
              child: const Text('Reset', style: TextStyle(color: Colors.red)),
            ),
          TextButton(
            onPressed: () => Navigator.pop(dialogCtx, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () async {
              await StorageService.saveAutoDevApiKey(controller.text.trim());
              if (dialogCtx.mounted) Navigator.pop(dialogCtx, true);
            },
            child: const Text('Save'),
          ),
        ],
      ),
    );

    if (saved == true && mounted) {
      setState(() {
        _vinErrorMessage = null;
        _isApiKeyError = false;
      });
      _showSnackBar(
        const SnackBar(
          content: Text('auto.dev API Key updated'),
          behavior: SnackBarBehavior.floating,
        ),
      );
      if (triggerDecode && _vinController.text.trim().isNotEmpty) {
        _decodeVin(_vinController.text);
      }
    }
  }

  Widget _buildVinErrorBanner(ThemeData theme) {
    if (_vinErrorMessage == null) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.only(top: 10.0),
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: theme.colorScheme.errorContainer,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: theme.colorScheme.error.withValues(alpha: 0.4),
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(Icons.error_outline,
                    color: theme.colorScheme.error, size: 20),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    _vinErrorMessage!,
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: theme.colorScheme.onErrorContainer,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ),
                InkWell(
                  onTap: () => setState(() => _vinErrorMessage = null),
                  child: Padding(
                    padding: const EdgeInsets.all(2.0),
                    child: Icon(Icons.close,
                        size: 18, color: theme.colorScheme.onErrorContainer),
                  ),
                ),
              ],
            ),
            if (_isApiKeyError) ...[
              const SizedBox(height: 8),
              Align(
                alignment: Alignment.centerRight,
                child: FilledButton.tonalIcon(
                  onPressed: _showApiKeyDialog,
                  icon: const Icon(Icons.key, size: 16),
                  label: const Text('Configure API Key'),
                  style: FilledButton.styleFrom(
                    visualDensity: VisualDensity.compact,
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // Submit
  // ---------------------------------------------------------------------------

  void _submitForm() {
    // Validate make/model (they're not inside TextFormField validators)
    bool hasError = false;
    if (_selectedMake == null || _selectedMake!.isEmpty) {
      setState(() => _makeError = true);
      hasError = true;
    }
    if (_selectedModel == null || _selectedModel!.isEmpty) {
      setState(() => _modelError = true);
      hasError = true;
    }
    if (hasError) return;

    if (_formKey.currentState!.validate()) {
      _formKey.currentState!.save();

      final newCar = Car(
        id: const Uuid().v4(),
        make: _selectedMake!,
        model: _selectedModel!,
        year: _selectedYear,
        imageUrl: _imageUrl,
        localImagePath: _localImagePath,
        licensePlate: _licensePlate?.isEmpty ?? true ? null : _licensePlate,
        vin: _vin?.isEmpty ?? true ? null : _vin,
        odometer: _odometer,
        isEv: _isEv,
        trim: _decodedTrim,
        bodyStyle: _decodedBodyStyle,
      );

      Navigator.pop(context, newCar);
    }
  }

  // ---------------------------------------------------------------------------
  // Build
  // ---------------------------------------------------------------------------

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final allMakes = getAllMakes();
    final currentModels =
        _selectedMake != null ? getModelsForMake(_selectedMake!) : <String>[];
    final hasImage = _imageUrl != null || _localImagePath != null;

    WidgetsBinding.instance.addPostFrameCallback((_) {
      _expandSheetIfKeyboardOpen();
    });

    final bottomInset = MediaQuery.of(context).viewInsets.bottom;

    return DraggableScrollableSheet(
      controller: _sheetController,
      initialChildSize: 0.75,
      minChildSize: 0.4,
      maxChildSize: 0.95,
      expand: false,
      builder: (context, scrollController) {
        return ScaffoldMessenger(
          key: _scaffoldMessengerKey,
          child: Scaffold(
            backgroundColor: Colors.transparent,
            resizeToAvoidBottomInset: false,
            body: Container(
              decoration: BoxDecoration(
                color: theme.colorScheme.surface,
                borderRadius:
                    const BorderRadius.vertical(top: Radius.circular(28.0)),
              ),
              child: CustomScrollView(
                controller: scrollController,
                keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
                slivers: [
                  // Header
                  SliverToBoxAdapter(
                    child: Column(
                      children: [
                        const SizedBox(height: 12),
                        Container(
                          width: 48,
                          height: 4,
                          decoration: BoxDecoration(
                            color: theme.colorScheme.onSurfaceVariant
                                .withValues(alpha: 0.4),
                            borderRadius: BorderRadius.circular(2),
                          ),
                        ),
                        const SizedBox(height: 24),
                        Text(
                          'Add New Vehicle',
                          style: theme.textTheme.headlineSmall,
                        ),
                        const SizedBox(height: 4),
                        Text(
                          'Select your make and model to get started',
                          style: theme.textTheme.bodyMedium?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                        ),
                        const SizedBox(height: 24),
                      ],
                    ),
                  ),

                  // Form content
                  SliverPadding(
                    padding: EdgeInsets.only(
                      left: 24.0,
                      right: 24.0,
                      bottom: bottomInset + 120.0,
                    ),
                    sliver: SliverToBoxAdapter(
                      child: Form(
                        key: _formKey,
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            // ── Quick Add by VIN helper card ──
                            _buildAnimatedField(
                              delay: 0.ms,
                              child: _buildVinHelperCard(theme),
                            ),
                            if (_vinErrorMessage != null) ...[
                              const SizedBox(height: 10),
                              _buildVinErrorBanner(theme),
                            ],
                            const SizedBox(height: 16),

                        // ── Make Picker ──
                        _buildAnimatedField(
                          delay: 50.ms,
                          child: _buildMakeField(allMakes, theme),
                        ),
                        const SizedBox(height: 16),

                        // ── Model Picker ──
                        _buildAnimatedField(
                          delay: 100.ms,
                          child: _buildModelField(currentModels, theme),
                        ),
                        const SizedBox(height: 16),

                        // ── Year Picker ──
                        _buildAnimatedField(
                          delay: 200.ms,
                          child: _buildYearField(theme),
                        ),
                        const SizedBox(height: 20),

                        // ── Photo Section ──
                        _buildAnimatedField(
                          delay: 300.ms,
                          child: _buildPhotoSection(theme, hasImage),
                        ),
                        const SizedBox(height: 20),

                        // ── EV Toggle ──
                        _buildAnimatedField(
                          delay: 350.ms,
                          child: _buildEvToggle(theme),
                        ),
                        const SizedBox(height: 16),

                        // ── License Plate ──
                        _buildAnimatedField(
                          delay: 400.ms,
                          child: TextFormField(
                            scrollPadding: _getScrollPadding(context),
                            decoration: const InputDecoration(
                              labelText: 'License Plate',
                              hintText: 'e.g., ABC-1234',
                              border: OutlineInputBorder(),
                              prefixIcon: Icon(Icons.badge_outlined),
                            ),
                            textInputAction: TextInputAction.next,
                            textCapitalization: TextCapitalization.characters,
                            onSaved: (value) => _licensePlate = value?.trim(),
                          ),
                        ),
                        const SizedBox(height: 16),

                        // ── VIN ──
                        _buildAnimatedField(
                          delay: 450.ms,
                          child: TextFormField(
                            controller: _vinController,
                            focusNode: _vinFocusNode,
                            scrollPadding: _getScrollPadding(context),
                            decoration: InputDecoration(
                              labelText: 'VIN',
                              hintText: 'Vehicle Identification Number',
                              border: const OutlineInputBorder(),
                              prefixIcon: const Icon(Icons.pin_outlined),
                              suffixIcon: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  if (_vinController.text.isNotEmpty)
                                    IconButton(
                                      icon: const Icon(Icons.clear, size: 18),
                                      onPressed: () {
                                        _vinController.clear();
                                        setState(() => _vin = null);
                                      },
                                    ),
                                  if (_isLoadingVin)
                                    const Padding(
                                      padding: EdgeInsets.all(12.0),
                                      child: SizedBox(
                                        width: 18,
                                        height: 18,
                                        child: CircularProgressIndicator(strokeWidth: 2),
                                      ),
                                    )
                                  else
                                    IconButton(
                                      icon: const Icon(Icons.auto_awesome),
                                      tooltip: 'Decode VIN & populate vehicle details via auto.dev',
                                      onPressed: () => _decodeVin(_vinController.text),
                                    ),
                                ],
                              ),
                            ),
                            textInputAction: TextInputAction.next,
                            textCapitalization: TextCapitalization.characters,
                            onChanged: (value) => _vin = value.trim(),
                            onFieldSubmitted: (value) => _decodeVin(value),
                            onSaved: (value) => _vin = value?.trim(),
                          ),
                        ),
                        const SizedBox(height: 16),

                        // ── Odometer ──
                        _buildAnimatedField(
                          delay: 500.ms,
                          child: TextFormField(
                            scrollPadding: _getScrollPadding(context),
                            decoration: const InputDecoration(
                              labelText: 'Current Odometer (miles)',
                              hintText: 'e.g., 45000',
                              border: OutlineInputBorder(),
                              prefixIcon: Icon(Icons.speed),
                            ),
                            keyboardType: TextInputType.number,
                            textInputAction: TextInputAction.done,
                            validator: (value) {
                              if (value != null && value.isNotEmpty) {
                                final val = int.tryParse(value);
                                if (val == null || val < 0) {
                                  return 'Please enter a valid odometer reading';
                                }
                              }
                              return null;
                            },
                            onFieldSubmitted: (_) => _submitForm(),
                            onSaved: (value) {
                              if (value != null && value.isNotEmpty) {
                                _odometer = int.tryParse(value);
                              }
                            },
                          ),
                        ),
                        const SizedBox(height: 32),

                        // ── Submit Button ──
                        _buildAnimatedField(
                          delay: 550.ms,
                          child: FilledButton.icon(
                            onPressed: _submitForm,
                            icon: const Icon(Icons.add),
                            label: const Text('Add Vehicle'),
                            style: FilledButton.styleFrom(
                              padding:
                                  const EdgeInsets.symmetric(vertical: 16),
                            ),
                          ),
                        ),
                        const SizedBox(height: 32),
                      ],
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  },
    );
  }

  // ---------------------------------------------------------------------------
  // Field Builders
  // ---------------------------------------------------------------------------

  /// Helper card at top for quick VIN decode via auto.dev.
  Widget _buildVinHelperCard(ThemeData theme) {
    return Card(
      elevation: 0,
      color: theme.colorScheme.primaryContainer.withValues(alpha: 0.35),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(
          color: theme.colorScheme.primary.withValues(alpha: 0.25),
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: theme.colorScheme.primary.withValues(alpha: 0.15),
                borderRadius: BorderRadius.circular(10),
              ),
              child: Icon(Icons.auto_awesome, color: theme.colorScheme.primary, size: 20),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Add by VIN',
                    style: theme.textTheme.titleSmall?.copyWith(
                      fontWeight: FontWeight.bold,
                      color: theme.colorScheme.onPrimaryContainer,
                    ),
                  ),
                  Text(
                    'Auto-populate make, model, year & photo via auto.dev',
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.onPrimaryContainer.withValues(alpha: 0.8),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            IconButton(
              icon: const Icon(Icons.key_outlined, size: 20),
              tooltip: 'Configure auto.dev API Key',
              onPressed: () => _showApiKeyDialog(),
            ),
            FilledButton.tonal(
              onPressed: _isLoadingVin ? null : _promptVinLookup,
              style: FilledButton.styleFrom(
                visualDensity: VisualDensity.compact,
              ),
              child: const Text('Enter VIN'),
            ),
          ],
        ),
      ),
    );
  }

  /// Searchable Make combobox using Autocomplete.
  Widget _buildMakeField(List<String> allMakes, ThemeData theme) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Autocomplete<String>(
          key: ValueKey('make_${_selectedMake ?? ""}'),
          initialValue: TextEditingValue(text: _selectedMake ?? ''),
          optionsBuilder: (TextEditingValue textEditingValue) {
            if (textEditingValue.text.isEmpty) {
              return allMakes;
            }
            return allMakes.where((make) =>
                make.toLowerCase().contains(textEditingValue.text.toLowerCase()));
          },
          onSelected: (String selection) {
            _onMakeSelected(selection);
          },
          fieldViewBuilder:
              (context, controller, focusNode, onFieldSubmitted) {
            return TextFormField(
              controller: controller,
              focusNode: focusNode,
              scrollPadding: _getScrollPadding(context),
              decoration: InputDecoration(
                labelText: 'Make *',
                hintText: 'Search makes…',
                border: const OutlineInputBorder(),
                prefixIcon: const Icon(Icons.directions_car_outlined),
                errorText: _makeError ? 'Please select a make' : null,
                suffixIcon: controller.text.isNotEmpty
                    ? IconButton(
                        icon: const Icon(Icons.clear, size: 18),
                        onPressed: () {
                          controller.clear();
                          setState(() {
                            _selectedMake = null;
                            _selectedModel = null;
                            _modelController.clear();
                            _imageUrl = null;
                            _localImagePath = null;
                            _availableImageUrls = [];
                            _selectedImageIndex = 0;
                            _makeError = false;
                          });
                        },
                      )
                    : null,
              ),
              textInputAction: TextInputAction.next,
              textCapitalization: TextCapitalization.words,
              onChanged: (value) {
                // If user is typing, clear current selection so they must pick
                if (value != _selectedMake) {
                  setState(() {
                    _selectedMake = null;
                    _selectedModel = null;
                    _modelController.clear();
                    _imageUrl = null;
                    _localImagePath = null;
                    _availableImageUrls = [];
                    _selectedImageIndex = 0;
                  });
                }
              },
            );
          },
          optionsViewBuilder: (context, onSelected, options) {
            return _buildOptionsOverlay(options, onSelected, theme);
          },
        ),
      ],
    );
  }

  /// Searchable Model combobox — enabled only when a make is selected.
  Widget _buildModelField(List<String> currentModels, ThemeData theme) {
    final isEnabled = _selectedMake != null;

    return Autocomplete<String>(
      key: ValueKey('model_${_selectedMake ?? ""}_${_selectedModel ?? ""}'),
      initialValue: TextEditingValue(text: _selectedModel ?? ''),
      optionsBuilder: (TextEditingValue textEditingValue) {
        if (!isEnabled) return const Iterable<String>.empty();
        if (textEditingValue.text.isEmpty) {
          return currentModels;
        }
        return currentModels.where((model) =>
            model.toLowerCase().contains(textEditingValue.text.toLowerCase()));
      },
      onSelected: (String selection) {
        _onModelSelected(selection);
      },
      fieldViewBuilder: (context, controller, focusNode, onFieldSubmitted) {
        return TextFormField(
          controller: controller,
          focusNode: focusNode,
          enabled: isEnabled,
          scrollPadding: _getScrollPadding(context),
          decoration: InputDecoration(
            labelText: 'Model *',
            hintText: isEnabled
                ? 'Search ${_selectedMake!} models…'
                : 'Select a make first',
            border: const OutlineInputBorder(),
            prefixIcon: const Icon(Icons.car_repair_outlined),
            errorText: _modelError ? 'Please select a model' : null,
            suffixIcon: controller.text.isNotEmpty && isEnabled
                ? IconButton(
                    icon: const Icon(Icons.clear, size: 18),
                    onPressed: () {
                      controller.clear();
                      setState(() {
                        _selectedModel = null;
                        _imageUrl = null;
                        _localImagePath = null;
                        _availableImageUrls = [];
                        _selectedImageIndex = 0;
                        _modelError = false;
                      });
                    },
                  )
                : null,
          ),
          textInputAction: TextInputAction.next,
          textCapitalization: TextCapitalization.words,
          onChanged: (value) {
            if (value != _selectedModel) {
              setState(() {
                _selectedModel = null;
                _imageUrl = null;
                _localImagePath = null;
                _availableImageUrls = [];
                _selectedImageIndex = 0;
              });
            }
          },
        );
      },
      optionsViewBuilder: (context, onSelected, options) {
        return _buildOptionsOverlay(options, onSelected, theme);
      },
    );
  }

  /// Year dropdown — defaults to 2010.
  Widget _buildYearField(ThemeData theme) {
    final currentYear = DateTime.now().year;
    final minYear = _selectedYear < 1980 ? _selectedYear : 1980;
    final maxYear = _selectedYear > currentYear + 1 ? _selectedYear : currentYear + 1;
    final years =
        List<int>.generate(maxYear - minYear + 1, (i) => maxYear - i);

    return DropdownButtonFormField<int>(
      key: ValueKey(_selectedYear),
      initialValue: _selectedYear,
      decoration: const InputDecoration(
        labelText: 'Year',
        border: OutlineInputBorder(),
        prefixIcon: Icon(Icons.calendar_today_outlined),
      ),
      items: years
          .map((year) => DropdownMenuItem(
                value: year,
                child: Text('$year'),
              ))
          .toList(),
      onChanged: (value) {
        if (value != null) {
          setState(() => _selectedYear = value);
        }
      },
    );
  }

  /// Photo section — preview card with carousel / pick / default / remove actions.
  Widget _buildPhotoSection(ThemeData theme, bool hasImage) {
    final showCarousel = _localImagePath == null && _availableImageUrls.length > 1;

    return Card(
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(
          color: theme.colorScheme.outlineVariant,
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Section label
            Row(
              children: [
                Icon(
                  showCarousel ? Icons.collections_outlined : Icons.photo_camera_outlined,
                  size: 20,
                  color: theme.colorScheme.onSurfaceVariant,
                ),
                const SizedBox(width: 8),
                Text(
                  showCarousel ? 'Vehicle Photos' : 'Vehicle Photo',
                  style: theme.textTheme.titleSmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
                if (showCarousel) ...[
                  const SizedBox(width: 8),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                    decoration: BoxDecoration(
                      color: theme.colorScheme.primaryContainer,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Text(
                      '${_availableImageUrls.length} available',
                      style: theme.textTheme.labelSmall?.copyWith(
                        color: theme.colorScheme.onPrimaryContainer,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                ],
              ],
            ),
            const SizedBox(height: 12),

            // Image preview or carousel
            if (showCarousel)
              _buildImageCarousel(theme)
            else
              _buildSingleImagePreview(theme, hasImage),

            // Horizontal thumbnail strip (when carousel is active)
            if (showCarousel) ...[
              const SizedBox(height: 10),
              _buildThumbnailStrip(theme),
            ],

            const SizedBox(height: 12),

            // Action buttons
            _buildPhotoActionButtons(theme, hasImage, showCarousel),
          ],
        ),
      ),
    );
  }

  /// Carousel view for navigating through multiple VIN photos.
  Widget _buildImageCarousel(ThemeData theme) {
    return Container(
      height: 200,
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Stack(
        fit: StackFit.expand,
        children: [
          PageView.builder(
            controller: _imagePageController,
            itemCount: _availableImageUrls.length,
            onPageChanged: (index) {
              setState(() {
                _selectedImageIndex = index;
                _imageUrl = _availableImageUrls[index];
                _localImagePath = null;
              });
            },
            itemBuilder: (context, index) {
              final url = _availableImageUrls[index];
              return Image.network(
                url,
                fit: BoxFit.cover,
                width: double.infinity,
                height: double.infinity,
                loadingBuilder: (context, child, loadingProgress) {
                  if (loadingProgress == null) return child;
                  return Center(
                    child: CircularProgressIndicator(
                      value: loadingProgress.expectedTotalBytes != null
                          ? loadingProgress.cumulativeBytesLoaded /
                              loadingProgress.expectedTotalBytes!
                          : null,
                      strokeWidth: 2,
                    ),
                  );
                },
                errorBuilder: (context, error, stackTrace) {
                  return Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(
                          Icons.broken_image_outlined,
                          size: 36,
                          color: theme.colorScheme.onSurfaceVariant
                              .withValues(alpha: 0.5),
                        ),
                        const SizedBox(height: 6),
                        Text(
                          'Photo unavailable',
                          style: theme.textTheme.bodySmall?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant
                                .withValues(alpha: 0.7),
                          ),
                        ),
                      ],
                    ),
                  );
                },
              );
            },
          ),

          // Previous button
          if (_selectedImageIndex > 0)
            Positioned(
              left: 8,
              top: 0,
              bottom: 0,
              child: Center(
                child: Material(
                  color: Colors.black.withValues(alpha: 0.45),
                  shape: const CircleBorder(),
                  clipBehavior: Clip.antiAlias,
                  child: IconButton(
                    icon: const Icon(Icons.chevron_left, color: Colors.white, size: 24),
                    tooltip: 'Previous photo',
                    padding: const EdgeInsets.all(4),
                    constraints: const BoxConstraints(minWidth: 36, minHeight: 36),
                    onPressed: () {
                      _imagePageController.previousPage(
                        duration: const Duration(milliseconds: 300),
                        curve: Curves.easeInOut,
                      );
                    },
                  ),
                ),
              ),
            ),

          // Next button
          if (_selectedImageIndex < _availableImageUrls.length - 1)
            Positioned(
              right: 8,
              top: 0,
              bottom: 0,
              child: Center(
                child: Material(
                  color: Colors.black.withValues(alpha: 0.45),
                  shape: const CircleBorder(),
                  clipBehavior: Clip.antiAlias,
                  child: IconButton(
                    icon: const Icon(Icons.chevron_right, color: Colors.white, size: 24),
                    tooltip: 'Next photo',
                    padding: const EdgeInsets.all(4),
                    constraints: const BoxConstraints(minWidth: 36, minHeight: 36),
                    onPressed: () {
                      _imagePageController.nextPage(
                        duration: const Duration(milliseconds: 300),
                        curve: Curves.easeInOut,
                      );
                    },
                  ),
                ),
              ),
            ),

          // Counter badge
          Positioned(
            top: 10,
            right: 10,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              decoration: BoxDecoration(
                color: Colors.black.withValues(alpha: 0.65),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(Icons.photo_library, color: Colors.white, size: 12),
                  const SizedBox(width: 5),
                  Text(
                    '${_selectedImageIndex + 1} / ${_availableImageUrls.length}',
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
            ),
          ),

          // Selected indicator
          Positioned(
            bottom: 10,
            left: 10,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              decoration: BoxDecoration(
                color: Colors.black.withValues(alpha: 0.65),
                borderRadius: BorderRadius.circular(8),
              ),
              child: const Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.check_circle, color: Colors.greenAccent, size: 14),
                  SizedBox(width: 5),
                  Text(
                    'Selected photo',
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 11,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  /// Horizontal thumbnail strip for rapid photo selection.
  Widget _buildThumbnailStrip(ThemeData theme) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(bottom: 6),
          child: Text(
            'Tap any photo to select:',
            style: theme.textTheme.labelSmall?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ),
        SizedBox(
          height: 56,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            itemCount: _availableImageUrls.length,
            separatorBuilder: (_, __) => const SizedBox(width: 8),
            itemBuilder: (context, index) {
              final isSelected = index == _selectedImageIndex;
              final url = _availableImageUrls[index];

              return GestureDetector(
                onTap: () {
                  setState(() {
                    _selectedImageIndex = index;
                    _imageUrl = url;
                    _localImagePath = null;
                  });
                  if (_imagePageController.hasClients) {
                    _imagePageController.animateToPage(
                      index,
                      duration: const Duration(milliseconds: 250),
                      curve: Curves.easeInOut,
                    );
                  }
                },
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 200),
                  width: 70,
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(
                      color: isSelected
                          ? theme.colorScheme.primary
                          : theme.colorScheme.outlineVariant.withValues(alpha: 0.6),
                      width: isSelected ? 2.5 : 1.0,
                    ),
                  ),
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(6),
                    child: Stack(
                      fit: StackFit.expand,
                      children: [
                        Image.network(
                          url,
                          fit: BoxFit.cover,
                          errorBuilder: (_, __, ___) => Container(
                            color: theme.colorScheme.surfaceContainerHighest,
                            child: Icon(
                              Icons.broken_image,
                              size: 16,
                              color: theme.colorScheme.onSurfaceVariant,
                            ),
                          ),
                        ),
                        if (isSelected)
                          Container(
                            color: theme.colorScheme.primary.withValues(alpha: 0.15),
                            alignment: Alignment.topRight,
                            padding: const EdgeInsets.all(2),
                            child: Icon(
                              Icons.check_circle,
                              color: theme.colorScheme.primary,
                              size: 14,
                            ),
                          ),
                      ],
                    ),
                  ),
                ),
              );
            },
          ),
        ),
      ],
    );
  }

  /// Single image preview when not in carousel mode.
  Widget _buildSingleImagePreview(ThemeData theme, bool hasImage) {
    return AnimatedContainer(
      duration: const Duration(milliseconds: 300),
      curve: Curves.easeInOut,
      height: hasImage ? 180 : 100,
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(12),
        image: _getPreviewImage(),
      ),
      child: !hasImage
          ? Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(
                    Icons.directions_car_outlined,
                    size: 36,
                    color: theme.colorScheme.onSurfaceVariant
                        .withValues(alpha: 0.4),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    _selectedModel != null
                        ? 'No image available'
                        : 'Select make & model for preview',
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant
                          .withValues(alpha: 0.6),
                    ),
                  ),
                ],
              ),
            )
          : (_localImagePath != null
              ? Align(
                  alignment: Alignment.bottomLeft,
                  child: Container(
                    margin: const EdgeInsets.all(10),
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: Colors.black.withValues(alpha: 0.65),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: const Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(Icons.photo, color: Colors.white, size: 13),
                        SizedBox(width: 4),
                        Text(
                          'Custom photo',
                          style: TextStyle(
                            color: Colors.white,
                            fontSize: 11,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ],
                    ),
                  ),
                )
              : null),
    );
  }

  /// Action buttons below the photo preview / carousel.
  Widget _buildPhotoActionButtons(
    ThemeData theme,
    bool hasImage,
    bool showCarousel,
  ) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            Expanded(
              child: OutlinedButton.icon(
                onPressed: _pickImage,
                icon: const Icon(Icons.photo_library_outlined, size: 18),
                label: const Text('Choose Photo'),
                style: OutlinedButton.styleFrom(
                  padding: const EdgeInsets.symmetric(vertical: 10),
                ),
              ),
            ),
            if (_selectedMake != null && _selectedModel != null) ...[
              const SizedBox(width: 8),
              Expanded(
                child: OutlinedButton.icon(
                  onPressed: (_localImagePath != null ||
                          (_availableImageUrls.length > 1 &&
                              _imageUrl !=
                                  getDefaultImageUrl(_selectedMake!, _selectedModel!)))
                      ? _useDefaultImage
                      : null,
                  icon: const Icon(Icons.auto_fix_high, size: 18),
                  label: const Text('Use Default'),
                  style: OutlinedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 10),
                  ),
                ),
              ),
            ],
            if (hasImage) ...[
              const SizedBox(width: 8),
              IconButton.outlined(
                onPressed: _removeImage,
                icon: const Icon(Icons.close, size: 18),
                tooltip: 'Remove image',
              ),
            ],
          ],
        ),
        if (_localImagePath != null && _availableImageUrls.isNotEmpty) ...[
          const SizedBox(height: 8),
          Center(
            child: TextButton.icon(
              onPressed: () {
                setState(() {
                  _localImagePath = null;
                  _imageUrl = _availableImageUrls[_selectedImageIndex];
                });
              },
              icon: const Icon(Icons.collections, size: 16),
              label: Text('Switch back to VIN photos (${_availableImageUrls.length})'),
            ),
          ),
        ],
        if (_localImagePath == null && _imageUrl == null && _availableImageUrls.isNotEmpty) ...[
          const SizedBox(height: 8),
          Center(
            child: TextButton.icon(
              onPressed: () {
                setState(() {
                  _imageUrl = _availableImageUrls[_selectedImageIndex];
                });
              },
              icon: const Icon(Icons.restore, size: 16),
              label: Text('Restore VIN photos (${_availableImageUrls.length})'),
            ),
          ),
        ],
      ],
    );
  }

  /// EV toggle with auto-detection badge.
  Widget _buildEvToggle(ThemeData theme) {
    final isAutoDetected = _selectedMake != null &&
        _selectedModel != null &&
        isKnownEv(_selectedMake!, _selectedModel!);

    return Card(
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(
          color: _isEv
              ? theme.colorScheme.primary.withValues(alpha: 0.5)
              : theme.colorScheme.outlineVariant,
        ),
      ),
      child: SwitchListTile(
        title: Row(
          children: [
            const Text('Electric Vehicle (EV)'),
            if (isAutoDetected) ...[
              const SizedBox(width: 8),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                decoration: BoxDecoration(
                  color: theme.colorScheme.primaryContainer,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Text(
                  'Auto',
                  style: theme.textTheme.labelSmall?.copyWith(
                    color: theme.colorScheme.onPrimaryContainer,
                  ),
                ),
              ),
            ],
          ],
        ),
        subtitle: const Text('Disables oil change reminders'),
        value: _isEv,
        secondary: Icon(
          Icons.electric_car,
          color: _isEv ? theme.colorScheme.primary : null,
        ),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
        ),
        onChanged: (bool value) {
          setState(() {
            _isEv = value;
          });
        },
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  DecorationImage? _getPreviewImage() {
    ImageProvider? provider;
    if (_localImagePath != null) {
      if (kIsWeb) {
        provider = NetworkImage(_localImagePath!);
      } else {
        provider = FileImage(File(_localImagePath!));
      }
    } else if (_imageUrl != null) {
      provider = NetworkImage(_imageUrl!);
    }
    if (provider == null) return null;
    return DecorationImage(image: provider, fit: BoxFit.cover);
  }

  /// Builds the dropdown overlay for Autocomplete options.
  Widget _buildOptionsOverlay(
    Iterable<String> options,
    AutocompleteOnSelected<String> onSelected,
    ThemeData theme,
  ) {
    return Align(
      alignment: Alignment.topLeft,
      child: Material(
        elevation: 4,
        borderRadius: BorderRadius.circular(12),
        clipBehavior: Clip.antiAlias,
        color: theme.colorScheme.surfaceContainer,
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxHeight: 220, maxWidth: 340),
          child: ListView.builder(
            padding: EdgeInsets.zero,
            shrinkWrap: true,
            itemCount: options.length,
            itemBuilder: (context, index) {
              final option = options.elementAt(index);
              return ListTile(
                dense: true,
                title: Text(option),
                onTap: () => onSelected(option),
              );
            },
          ),
        ),
      ),
    );
  }

  Widget _buildAnimatedField(
      {required Widget child, required Duration delay}) {
    return child.animate().fadeIn(duration: 400.ms, delay: delay).slideY(
        begin: 0.2,
        end: 0,
        duration: 400.ms,
        delay: delay,
        curve: Curves.easeOutQuad);
  }
}
