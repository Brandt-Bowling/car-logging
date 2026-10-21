package com.example.car_logger.data.vehicle

object VehicleData {
    val vehicleMakeModels: Map<String, List<String>> = mapOf(
        "Acura" to listOf("ILX", "Integra", "MDX", "RDX", "TLX", "ZDX"),
        "Alfa Romeo" to listOf("Giulia", "Stelvio", "Tonale"),
        "Audi" to listOf(
            "A3", "A4", "A5", "A6", "A7", "A8",
            "e-tron GT", "Q3", "Q4 e-tron", "Q5", "Q7", "Q8", "Q8 e-tron",
            "RS 3", "RS 5", "RS 6", "RS 7", "RS e-tron GT",
            "S3", "S4", "S5", "S6", "S7", "S8",
            "TT"
        ),
        "BMW" to listOf(
            "2 Series", "3 Series", "4 Series", "5 Series", "7 Series", "8 Series",
            "i4", "i5", "i7", "iX",
            "M2", "M3", "M4", "M5", "M8",
            "X1", "X2", "X3", "X4", "X5", "X6", "X7",
            "Z4"
        ),
        "Buick" to listOf("Enclave", "Encore", "Encore GX", "Envision", "Envista"),
        "Cadillac" to listOf("CT4", "CT5", "Escalade", "Escalade-V", "Lyriq", "XT4", "XT5", "XT6"),
        "Chevrolet" to listOf(
            "Blazer", "Blazer EV", "Bolt EUV", "Bolt EV", "Camaro",
            "Colorado", "Corvette", "Equinox", "Equinox EV",
            "Malibu", "Silverado", "Silverado EV", "Sonic",
            "Suburban", "Tahoe", "Trailblazer", "Traverse", "Trax"
        ),
        "Chrysler" to listOf("300", "Pacifica", "Pacifica Hybrid"),
        "Dodge" to listOf("Challenger", "Charger", "Durango", "Hornet"),
        "Ferrari" to listOf("296 GTB", "296 GTS", "812 Superfast", "F8 Tributo", "Purosangue", "Roma", "SF90 Stradale"),
        "Fiat" to listOf("500", "500e", "500X"),
        "Ford" to listOf(
            "Bronco", "Bronco Sport", "Edge", "Escape", "Expedition",
            "Explorer", "F-150", "F-150 Lightning", "Maverick",
            "Mustang", "Mustang Mach-E", "Ranger", "Super Duty"
        ),
        "Genesis" to listOf("Electrified G80", "Electrified GV70", "G70", "G80", "G90", "GV60", "GV70", "GV80"),
        "GMC" to listOf("Acadia", "Canyon", "Hummer EV", "Sierra", "Terrain", "Yukon"),
        "Honda" to listOf("Accord", "Civic", "CR-V", "HR-V", "Odyssey", "Passport", "Pilot", "Prologue", "Ridgeline"),
        "Hyundai" to listOf("Elantra", "Ioniq 5", "Ioniq 6", "Kona", "Kona Electric", "Palisade", "Santa Cruz", "Santa Fe", "Sonata", "Tucson", "Venue"),
        "Infiniti" to listOf("Q50", "Q60", "QX50", "QX55", "QX60", "QX80"),
        "Jaguar" to listOf("E-PACE", "F-PACE", "F-TYPE", "I-PACE", "XF"),
        "Jeep" to listOf("Cherokee", "Compass", "Gladiator", "Grand Cherokee", "Grand Cherokee 4xe", "Grand Wagoneer", "Renegade", "Wagoneer", "Wrangler", "Wrangler 4xe"),
        "Kia" to listOf("Carnival", "EV6", "EV9", "Forte", "K5", "Niro", "Niro EV", "Rio", "Seltos", "Sorento", "Soul", "Sportage", "Stinger", "Telluride"),
        "Lamborghini" to listOf("Huracán", "Revuelto", "Urus"),
        "Land Rover" to listOf("Defender", "Discovery", "Discovery Sport", "Range Rover", "Range Rover Evoque", "Range Rover Sport", "Range Rover Velar"),
        "Lexus" to listOf("ES", "GX", "IS", "LC", "LS", "LX", "NX", "RC", "RX", "RZ", "TX", "UX"),
        "Lincoln" to listOf("Aviator", "Corsair", "Nautilus", "Navigator"),
        "Lucid" to listOf("Air", "Gravity"),
        "Maserati" to listOf("Ghibli", "GranTurismo", "Grecale", "Levante", "MC20", "Quattroporte"),
        "Mazda" to listOf("CX-30", "CX-5", "CX-50", "CX-70", "CX-90", "Mazda3", "MX-5 Miata", "MX-30"),
        "Mercedes-Benz" to listOf("A-Class", "AMG GT", "C-Class", "CLA", "CLE", "E-Class", "EQB", "EQE", "EQE SUV", "EQS", "EQS SUV", "G-Class", "GLA", "GLB", "GLC", "GLE", "GLS", "Maybach S-Class", "S-Class", "SL"),
        "Mini" to listOf("Clubman", "Cooper", "Cooper SE", "Countryman"),
        "Mitsubishi" to listOf("Eclipse Cross", "Mirage", "Outlander", "Outlander PHEV"),
        "Nissan" to listOf("Altima", "Ariya", "Frontier", "Kicks", "Leaf", "Maxima", "Murano", "Pathfinder", "Rogue", "Sentra", "Titan", "Versa", "Z"),
        "Polestar" to listOf("2", "3", "4"),
        "Porsche" to listOf("718 Boxster", "718 Cayman", "911", "Cayenne", "Macan", "Macan Electric", "Panamera", "Taycan"),
        "Ram" to listOf("1500", "2500", "3500", "ProMaster"),
        "Rivian" to listOf("R1S", "R1T", "R2", "R3"),
        "Rolls-Royce" to listOf("Cullinan", "Ghost", "Phantom", "Spectre", "Wraith"),
        "Subaru" to listOf("Ascent", "BRZ", "Crosstrek", "Forester", "Impreza", "Legacy", "Outback", "Solterra", "WRX"),
        "Tesla" to listOf("Cybertruck", "Model 3", "Model S", "Model X", "Model Y"),
        "Toyota" to listOf("4Runner", "bZ4X", "Camry", "Corolla", "Corolla Cross", "Crown", "GR Corolla", "GR Supra", "GR86", "Grand Highlander", "Highlander", "Land Cruiser", "Prius", "RAV4", "RAV4 Prime", "Sequoia", "Tacoma", "Tundra", "Venza"),
        "Volkswagen" to listOf("Arteon", "Atlas", "Atlas Cross Sport", "Golf", "Golf GTI", "Golf R", "ID.4", "ID.Buzz", "Jetta", "Jetta GLI", "Taos", "Tiguan"),
        "Volvo" to listOf("C40 Recharge", "EX30", "EX90", "S60", "S90", "V60", "V90", "XC40", "XC60", "XC90")
    )

    val defaultVehicleImages: Map<String, String> = mapOf(
        "Tesla|Model 3" to "https://api.auto.dev/photos/retail/5YJ3E1EAXMF860954-1.jpg",
        "Tesla|Model Y" to "https://upload.wikimedia.org/wikipedia/commons/5/5c/2024_Tesla_Model_Y_Long_Range_in_Ultra_White%2C_front_6.15.2024.jpg",
        "Tesla|Model S" to "https://upload.wikimedia.org/wikipedia/commons/1/15/2018_Tesla_Model_S_75D.jpg",
        "Tesla|Model X" to "https://upload.wikimedia.org/wikipedia/commons/5/53/Tesla_Model_X_Geneva_2016.jpg",
        "Toyota|Camry" to "https://upload.wikimedia.org/wikipedia/commons/a/ac/2018_Toyota_Camry_%28ASV70R%29_Ascent_sedan_%282018-08-27%29_01.jpg",
        "Toyota|Corolla" to "https://upload.wikimedia.org/wikipedia/commons/d/d1/2019_Toyota_Corolla_Hybrid_Design_1.8.jpg",
        "Toyota|RAV4" to "https://upload.wikimedia.org/wikipedia/commons/a/a6/2019_Toyota_RAV4_Adventure_%28facelift%29_front_8.24.19.jpg",
        "Toyota|Prius" to "https://upload.wikimedia.org/wikipedia/commons/7/71/2023_Toyota_Prius_Limited_%28XW60%29%2C_front_3.16.23.jpg",
        "Toyota|Tacoma" to "https://upload.wikimedia.org/wikipedia/commons/8/82/2024_Toyota_Tacoma_TRD_Sport_Premium_in_Underground%2C_front_7.27.2024.jpg",
        "Honda|Civic" to "https://upload.wikimedia.org/wikipedia/commons/2/2c/2022_Honda_Civic_Touring_in_Meteorite_Gray_Metallic%2C_Front_Left%2C_12-25-2021.jpg",
        "Honda|Accord" to "https://upload.wikimedia.org/wikipedia/commons/8/8b/2021_Honda_Accord_Sport_SE_%28facelift%29%2C_front_10.15.21.jpg",
        "Honda|CR-V" to "https://upload.wikimedia.org/wikipedia/commons/e/e9/2023_Honda_CR-V_EX-L_in_Canyon_River_Blue%2C_Front_Left%2C_11-12-2022.jpg",
        "Ford|F-150" to "https://upload.wikimedia.org/wikipedia/commons/1/1e/2021_Ford_F-150_Lariat_with_FX4_Off-Road_Package%2C_front_8.14.21.jpg",
        "Ford|Mustang" to "https://upload.wikimedia.org/wikipedia/commons/1/1c/2024_Ford_Mustang_GT_Fastback_in_Vapor_Blue%2C_Front_Left%2C_07-20-2024.jpg",
        "Ford|Bronco" to "https://upload.wikimedia.org/wikipedia/commons/4/46/2021_Ford_Bronco_Badlands_Sasquatch_Package%2C_front_7.24.21.jpg",
        "Chevrolet|Blazer EV" to "https://api.auto.dev/photos/retail/3GNKDCRJ1RS284858-1.jpg",
        "Chevrolet|Corvette" to "https://upload.wikimedia.org/wikipedia/commons/b/bf/2020_Chevrolet_Corvette_C8_Stingray_Coupe_in_Torch_Red%2C_front_10.3.20.jpg",
        "Chevrolet|Silverado" to "https://upload.wikimedia.org/wikipedia/commons/c/cf/2019_Chevrolet_Silverado_LT_Trail_Boss%2C_front_5.3.19.jpg",
        "Chevrolet|Sonic" to "https://upload.wikimedia.org/wikipedia/commons/f/f9/2012_Chevrolet_Sonic_1LT_hatchback_--_10-19-2011_front_1.jpg",
        "BMW|3 Series" to "https://upload.wikimedia.org/wikipedia/commons/5/5e/2019_BMW_330i_M_Sport_automatic_2.0_Front.jpg",
        "Hyundai|Ioniq 5" to "https://upload.wikimedia.org/wikipedia/commons/f/f7/Hyundai_Ioniq_5_72%2C6_kWh_Allradantrieb_Uniq_%28NE1%29_%E2%80%93_f_20210612.jpg",
        "Kia|EV6" to "https://upload.wikimedia.org/wikipedia/commons/7/7a/2022_Kia_EV6_Wind_AWD_in_Glacier%2C_front_6.25.22.jpg",
        "Kia|Telluride" to "https://upload.wikimedia.org/wikipedia/commons/7/76/2020_Kia_Telluride_S%2C_front_10.11.19.jpg",
        "Subaru|Outback" to "https://upload.wikimedia.org/wikipedia/commons/e/e8/2020_Subaru_Outback_Touring_XT%2C_front_1.5.20.jpg",
        "Subaru|Crosstrek" to "https://upload.wikimedia.org/wikipedia/commons/b/b6/2024_Subaru_Crosstrek_Premium_in_Oasis_Turquoise%2C_Front_Left%2C_09-09-2023.jpg",
        "Jeep|Wrangler" to "https://upload.wikimedia.org/wikipedia/commons/a/a2/2019_Jeep_Wrangler_Sahara_2.0L_front_4.28.19.jpg",
        "Jeep|Grand Cherokee" to "https://upload.wikimedia.org/wikipedia/commons/7/72/2022_Jeep_Grand_Cherokee_Summit_Reserve_4xe%2C_front_6.25.22.jpg",
        "Porsche|911" to "https://upload.wikimedia.org/wikipedia/commons/5/5b/Porsche_992_Carrera_4S_%28Crayon%29.jpg",
        "Porsche|Taycan" to "https://upload.wikimedia.org/wikipedia/commons/6/6e/Porsche_Taycan_at_IAA_2019_IMG_0250.jpg",
        "Volkswagen|Golf GTI" to "https://upload.wikimedia.org/wikipedia/commons/8/8c/2022_Volkswagen_Golf_GTI_in_Kings_Red_Metallic%2C_Front_Left%2C_11-20-2021.jpg",
        "Rivian|R1T" to "https://upload.wikimedia.org/wikipedia/commons/1/13/Rivian_R1T_Launch_Edition_FR_%28cropped%29.jpg",
        "Nissan|Rogue" to "https://upload.wikimedia.org/wikipedia/commons/7/72/2021_Nissan_Rogue_SV_in_Scarlet_Ember%2C_front_1.4.21.jpg",
        "Mazda|MX-5 Miata" to "https://upload.wikimedia.org/wikipedia/commons/8/8c/Mazda_MX-5_ND_front.jpg"
    )

    val knownEvModels: Set<String> = setOf(
        "Audi|e-tron GT", "Audi|Q4 e-tron", "Audi|Q8 e-tron", "Audi|RS e-tron GT",
        "BMW|i4", "BMW|i5", "BMW|i7", "BMW|iX",
        "Cadillac|Lyriq",
        "Chevrolet|Blazer EV", "Chevrolet|Bolt EUV", "Chevrolet|Bolt EV", "Chevrolet|Equinox EV", "Chevrolet|Silverado EV",
        "Fiat|500e",
        "Ford|F-150 Lightning", "Ford|Mustang Mach-E",
        "Genesis|Electrified G80", "Genesis|Electrified GV70", "Genesis|GV60",
        "GMC|Hummer EV",
        "Honda|Prologue",
        "Hyundai|Ioniq 5", "Hyundai|Ioniq 6", "Hyundai|Kona Electric",
        "Jaguar|I-PACE",
        "Kia|EV6", "Kia|EV9", "Kia|Niro EV",
        "Lexus|RZ",
        "Lucid|Air", "Lucid|Gravity",
        "Mercedes-Benz|EQB", "Mercedes-Benz|EQE", "Mercedes-Benz|EQE SUV", "Mercedes-Benz|EQS", "Mercedes-Benz|EQS SUV",
        "Mini|Cooper SE",
        "Nissan|Ariya", "Nissan|Leaf",
        "Polestar|2", "Polestar|3", "Polestar|4",
        "Porsche|Macan Electric", "Porsche|Taycan",
        "Rivian|R1S", "Rivian|R1T", "Rivian|R2", "Rivian|R3",
        "Rolls-Royce|Spectre",
        "Subaru|Solterra",
        "Tesla|Cybertruck", "Tesla|Model 3", "Tesla|Model S", "Tesla|Model X", "Tesla|Model Y",
        "Toyota|bZ4X",
        "Volkswagen|ID.4", "Volkswagen|ID.Buzz",
        "Volvo|C40 Recharge", "Volvo|EX30", "Volvo|EX90"
    )

    fun getAllMakes(): List<String> = vehicleMakeModels.keys.sortedWith(String.CASE_INSENSITIVE_ORDER)

    fun getModelsForMake(make: String): List<String> = vehicleMakeModels[make] ?: emptyList()

    fun getDefaultImageUrl(make: String, model: String): String? = defaultVehicleImages["$make|$model"]

    fun isKnownEv(make: String, model: String): Boolean = knownEvModels.contains("$make|$model")

    fun sanitizeImageUrl(url: String): String {
        val thumbPrefix = "upload.wikimedia.org/wikipedia/commons/thumb/"
        if (url.contains(thumbPrefix)) {
            val regex = Regex("""/wikipedia/commons/thumb/([^/]+/[^/]+/[^/]+)/[^/]+$""")
            val match = regex.find(url)
            if (match != null) {
                return url.replace(regex, "/wikipedia/commons/${match.groupValues[1]}")
            }
        }
        return url
    }
}
