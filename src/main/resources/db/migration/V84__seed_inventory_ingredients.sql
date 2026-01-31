-- Seed inventory ingredients for restaurant
-- Categories: Vegetables, Fruits, Meat & Poultry, Fish & Seafood, Dairy, Rice & Grains,
-- Flour & Bakery, Spices & Seasonings, Oils & Fats, Beverages, Canned Goods, Dried Foods,
-- Nuts & Seeds, Legumes, Condiments & Sauces, Sweets & Desserts, Tea & Coffee,
-- Eggs & Honey, Kitchen Supplies, Cleaning Supplies, Packaging Materials

-- ============================================================================
-- CATEGORY 1: VEGETABLES (Sabzavotlar) - 80 items
-- ============================================================================
INSERT INTO inventory_ingredients (restaurant_id, name, unit, minimum_stock, reorder_level, track_inventory) VALUES
-- Leafy Greens
(1, 'Ukrop (Ko''katlar)', 'bog''lam', 10, 15, TRUE),
(1, 'Петрушка (Petrushka)', 'bog''lam', 10, 15, TRUE),
(1, 'Kinza (Kashnich)', 'bog''lam', 10, 15, TRUE),
(1, 'Rayxon (Reygan)', 'bog''lam', 5, 10, TRUE),
(1, 'Shivit', 'bog''lam', 5, 10, TRUE),
(1, 'Ko''k piyoz', 'bog''lam', 10, 15, TRUE),
(1, 'Salat bargi', 'kg', 3, 5, TRUE),
(1, 'Pekin karamи', 'kg', 3, 5, TRUE),
(1, 'Ismaloq', 'kg', 2, 3, TRUE),
(1, 'Karam', 'kg', 5, 10, TRUE),
-- Root Vegetables
(1, 'Kartoshka', 'kg', 20, 30, TRUE),
(1, 'Sabzi (Katta)', 'kg', 10, 15, TRUE),
(1, 'Sabzi (Kichik)', 'kg', 5, 10, TRUE),
(1, 'Piyoz (Oq)', 'kg', 15, 25, TRUE),
(1, 'Piyoz (Qizil)', 'kg', 10, 15, TRUE),
(1, 'Sarimsoq', 'kg', 3, 5, TRUE),
(1, 'Sarimsoq (Bosh)', 'dona', 50, 80, TRUE),
(1, 'Lavlagi (Qizilcha)', 'kg', 3, 5, TRUE),
(1, 'Turp (Oq)', 'kg', 2, 3, TRUE),
(1, 'Turp (Qizil)', 'kg', 2, 3, TRUE),
(1, 'Sholg''om', 'kg', 2, 3, TRUE),
(1, 'Zanjabil (Imbir)', 'kg', 1, 2, TRUE),
(1, 'Yer yong''oq', 'kg', 2, 3, TRUE),
-- Solanaceae (Nightshades)
(1, 'Pomidor (Katta)', 'kg', 15, 25, TRUE),
(1, 'Pomidor (Kichik/Cherry)', 'kg', 5, 8, TRUE),
(1, 'Pomidor (Uzum shakli)', 'kg', 3, 5, TRUE),
(1, 'Baqlajon (Katta)', 'kg', 5, 8, TRUE),
(1, 'Baqlajon (Kichik)', 'kg', 3, 5, TRUE),
(1, 'Qalampir (Bulgar, Qizil)', 'kg', 5, 8, TRUE),
(1, 'Qalampir (Bulgar, Sariq)', 'kg', 3, 5, TRUE),
(1, 'Qalampir (Bulgar, Yashil)', 'kg', 5, 8, TRUE),
(1, 'Qalampir (Achchiq, Qizil)', 'kg', 1, 2, TRUE),
(1, 'Qalampir (Achchiq, Yashil)', 'kg', 1, 2, TRUE),
-- Cucurbits
(1, 'Bodring (Uzun)', 'kg', 10, 15, TRUE),
(1, 'Bodring (Kichik/Pickling)', 'kg', 5, 8, TRUE),
(1, 'Qovoq (Yashil)', 'kg', 5, 8, TRUE),
(1, 'Qovoq (Sariq)', 'kg', 3, 5, TRUE),
(1, 'Kabachok', 'kg', 5, 8, TRUE),
(1, 'Oshqovoq', 'kg', 5, 8, TRUE),
(1, 'Tarvuz', 'kg', 10, 15, TRUE),
(1, 'Qovun', 'kg', 10, 15, TRUE),
-- Brassicas
(1, 'Gulkaram (Cauliflower)', 'kg', 3, 5, TRUE),
(1, 'Brokkoli', 'kg', 2, 3, TRUE),
(1, 'Bryussel karamи', 'kg', 1, 2, TRUE),
(1, 'Kolrabi', 'kg', 1, 2, TRUE),
-- Alliums
(1, 'Porey (Leek)', 'kg', 2, 3, TRUE),
(1, 'Shallot piyoz', 'kg', 1, 2, TRUE),
-- Legume Vegetables (Fresh)
(1, 'Yashil no''xat (Fresh)', 'kg', 3, 5, TRUE),
(1, 'Loviya (Yashil, Fresh)', 'kg', 3, 5, TRUE),
(1, 'Mosh (Fresh)', 'kg', 2, 3, TRUE),
-- Other Vegetables
(1, 'Makkajo''xori (Fresh)', 'dona', 20, 30, TRUE),
(1, 'Qo''ziqorin (Shampinon)', 'kg', 3, 5, TRUE),
(1, 'Qo''ziqorin (Oyster)', 'kg', 2, 3, TRUE),
(1, 'Qo''ziqorin (Portobello)', 'kg', 1, 2, TRUE),
(1, 'Sparjа (Asparagus)', 'kg', 1, 2, TRUE),
(1, 'Artishok', 'kg', 1, 2, TRUE),
(1, 'Seldereу (Karefsе)', 'kg', 2, 3, TRUE),
(1, 'Seldereу ildizi', 'kg', 1, 2, TRUE),
(1, 'Fenxel', 'kg', 1, 2, TRUE),
(1, 'Revеn', 'kg', 1, 2, TRUE),
-- Sprouts and Microgreens
(1, 'Mosh unib chiqqan', 'kg', 1, 2, TRUE),
(1, 'Mikrogreen (Aralash)', 'kg', 0.5, 1, TRUE),
(1, 'Bug''doy unib chiqqan', 'kg', 0.5, 1, TRUE),
-- Frozen Vegetables
(1, 'Muzlatilgan no''xat', 'kg', 5, 10, TRUE),
(1, 'Muzlatilgan loviya', 'kg', 3, 5, TRUE),
(1, 'Muzlatilgan makkajo''xori', 'kg', 3, 5, TRUE),
(1, 'Muzlatilgan aralash sabzavotlar', 'kg', 5, 10, TRUE),
(1, 'Muzlatilgan brokkoli', 'kg', 2, 3, TRUE),
(1, 'Muzlatilgan gulkaram', 'kg', 2, 3, TRUE),
(1, 'Muzlatilgan qalampir', 'kg', 2, 3, TRUE),
-- Pickled/Preserved Vegetables (Fresh stock)
(1, 'Karam (Tuzlash uchun)', 'kg', 5, 10, TRUE),
(1, 'Bodring (Tuzlash uchun)', 'kg', 3, 5, TRUE),
(1, 'Pomidor (Tuzlash uchun)', 'kg', 3, 5, TRUE),
-- Specialty/Imported
(1, 'Avokado', 'dona', 10, 15, TRUE),
(1, 'Jambu (Okra)', 'kg', 1, 2, TRUE),
(1, 'Xitoy karamи (Bok choy)', 'kg', 2, 3, TRUE),
(1, 'Daikon turp', 'kg', 1, 2, TRUE);

-- ============================================================================
-- CATEGORY 2: FRUITS (Mevalar) - 60 items
-- ============================================================================
INSERT INTO inventory_ingredients (restaurant_id, name, unit, minimum_stock, reorder_level, track_inventory) VALUES
-- Citrus Fruits
(1, 'Apelsin', 'kg', 10, 15, TRUE),
(1, 'Mandarin', 'kg', 8, 12, TRUE),
(1, 'Limon', 'kg', 5, 8, TRUE),
(1, 'Laym', 'kg', 2, 3, TRUE),
(1, 'Greypfrut', 'kg', 3, 5, TRUE),
(1, 'Pomelo', 'kg', 2, 3, TRUE),
-- Pome Fruits
(1, 'Olma (Qizil)', 'kg', 10, 15, TRUE),
(1, 'Olma (Yashil)', 'kg', 8, 12, TRUE),
(1, 'Olma (Sariq)', 'kg', 5, 8, TRUE),
(1, 'Nok', 'kg', 5, 8, TRUE),
(1, 'Behi', 'kg', 3, 5, TRUE),
-- Stone Fruits
(1, 'Shaftoli', 'kg', 5, 8, TRUE),
(1, 'Nektarin', 'kg', 3, 5, TRUE),
(1, 'O''rik', 'kg', 5, 8, TRUE),
(1, 'Olcha', 'kg', 3, 5, TRUE),
(1, 'Gilos', 'kg', 3, 5, TRUE),
(1, 'Olxo''ri', 'kg', 3, 5, TRUE),
-- Berries
(1, 'Qulupnay (Strawberry)', 'kg', 3, 5, TRUE),
(1, 'Malina (Raspberry)', 'kg', 2, 3, TRUE),
(1, 'Ko''k malina (Blueberry)', 'kg', 2, 3, TRUE),
(1, 'Qora smorodina', 'kg', 2, 3, TRUE),
(1, 'Qizil smorodina', 'kg', 1, 2, TRUE),
(1, 'Krыjovnik', 'kg', 1, 2, TRUE),
(1, 'Blackberry', 'kg', 1, 2, TRUE),
(1, 'Tut (Mulberry)', 'kg', 2, 3, TRUE),
-- Grapes
(1, 'Uzum (Oq)', 'kg', 5, 8, TRUE),
(1, 'Uzum (Qizil)', 'kg', 5, 8, TRUE),
(1, 'Uzum (Qora)', 'kg', 3, 5, TRUE),
(1, 'Uzum (Kishmish)', 'kg', 3, 5, TRUE),
-- Tropical Fruits
(1, 'Banan', 'kg', 10, 15, TRUE),
(1, 'Ananas', 'dona', 5, 8, TRUE),
(1, 'Mango', 'kg', 3, 5, TRUE),
(1, 'Papaya', 'kg', 2, 3, TRUE),
(1, 'Kivi', 'kg', 3, 5, TRUE),
(1, 'Anor (Granat)', 'kg', 5, 8, TRUE),
(1, 'Xurmo', 'kg', 5, 8, TRUE),
(1, 'Feyhoa', 'kg', 2, 3, TRUE),
(1, 'Litchi', 'kg', 1, 2, TRUE),
(1, 'Dragon fruit', 'kg', 1, 2, TRUE),
(1, 'Marakuya (Passion fruit)', 'kg', 1, 2, TRUE),
(1, 'Kokos', 'dona', 5, 8, TRUE),
-- Melons
(1, 'Qovun (Torpedo)', 'kg', 10, 15, TRUE),
(1, 'Qovun (Oq urug'')', 'kg', 8, 12, TRUE),
(1, 'Tarvuz (Qizil)', 'kg', 15, 25, TRUE),
(1, 'Tarvuz (Sariq)', 'kg', 5, 8, TRUE),
-- Dried Fruits (Fresh for drying)
(1, 'Anjir (Fresh)', 'kg', 3, 5, TRUE),
(1, 'Hurmo (Fresh dates)', 'kg', 2, 3, TRUE),
-- Frozen Fruits
(1, 'Muzlatilgan qulupnay', 'kg', 3, 5, TRUE),
(1, 'Muzlatilgan malina', 'kg', 2, 3, TRUE),
(1, 'Muzlatilgan ko''k malina', 'kg', 2, 3, TRUE),
(1, 'Muzlatilgan olcha', 'kg', 2, 3, TRUE),
(1, 'Muzlatilgan mango', 'kg', 2, 3, TRUE),
(1, 'Muzlatilgan aralash mevalar', 'kg', 3, 5, TRUE),
-- Specialty Fruits
(1, 'Yulduz mevasi (Starfruit)', 'kg', 1, 2, TRUE),
(1, 'Rambutan', 'kg', 1, 2, TRUE),
(1, 'Guava', 'kg', 1, 2, TRUE),
(1, 'Durian', 'kg', 1, 2, TRUE),
(1, 'Jackfruit', 'kg', 1, 2, TRUE),
(1, 'Longan', 'kg', 1, 2, TRUE);

-- ============================================================================
-- CATEGORY 3: MEAT & POULTRY (Go'sht va parranda) - 70 items
-- ============================================================================
INSERT INTO inventory_ingredients (restaurant_id, name, unit, minimum_stock, reorder_level, track_inventory) VALUES
-- Beef (Mol go'shti)
(1, 'Mol go''shti (Bifteklik)', 'kg', 10, 15, TRUE),
(1, 'Mol go''shti (Antrekot)', 'kg', 8, 12, TRUE),
(1, 'Mol go''shti (Rib-eye)', 'kg', 5, 8, TRUE),
(1, 'Mol go''shti (Tenderloin)', 'kg', 5, 8, TRUE),
(1, 'Mol go''shti (Suyakli)', 'kg', 10, 15, TRUE),
(1, 'Mol go''shti (Qiyma)', 'kg', 15, 20, TRUE),
(1, 'Mol go''shti (Qovurma uchun)', 'kg', 10, 15, TRUE),
(1, 'Mol go''shti (Sho''rva uchun)', 'kg', 10, 15, TRUE),
(1, 'Mol go''shti (Kabob uchun)', 'kg', 10, 15, TRUE),
(1, 'Mol jigar', 'kg', 3, 5, TRUE),
(1, 'Mol yurak', 'kg', 2, 3, TRUE),
(1, 'Mol tili', 'kg', 2, 3, TRUE),
(1, 'Mol buyrak', 'kg', 2, 3, TRUE),
(1, 'Mol suyagi (Bulyon uchun)', 'kg', 5, 8, TRUE),
(1, 'Mol yog''i', 'kg', 3, 5, TRUE),
-- Lamb/Mutton (Qo'y go'shti)
(1, 'Qo''y go''shti (Bifteklik)', 'kg', 8, 12, TRUE),
(1, 'Qo''y go''shti (Qovurmali)', 'kg', 10, 15, TRUE),
(1, 'Qo''y go''shti (Kabob uchun)', 'kg', 10, 15, TRUE),
(1, 'Qo''y go''shti (Sho''rva uchun)', 'kg', 10, 15, TRUE),
(1, 'Qo''y go''shti (Qiyma)', 'kg', 10, 15, TRUE),
(1, 'Qo''y jigar', 'kg', 3, 5, TRUE),
(1, 'Qo''y dumba', 'kg', 5, 8, TRUE),
(1, 'Qo''y suyagi', 'kg', 5, 8, TRUE),
(1, 'Qo''y boshi', 'dona', 5, 8, TRUE),
(1, 'Qo''y oyog''i', 'dona', 10, 15, TRUE),
(1, 'Qo''zi go''shti', 'kg', 5, 8, TRUE),
-- Goat (Echki go'shti)
(1, 'Echki go''shti', 'kg', 3, 5, TRUE),
-- Chicken (Tovuq)
(1, 'Tovuq (Butun)', 'dona', 20, 30, TRUE),
(1, 'Tovuq ko''krak (Fillet)', 'kg', 15, 20, TRUE),
(1, 'Tovuq son', 'kg', 15, 20, TRUE),
(1, 'Tovuq qanoti', 'kg', 10, 15, TRUE),
(1, 'Tovuq oyoq', 'kg', 10, 15, TRUE),
(1, 'Tovuq qiyma', 'kg', 10, 15, TRUE),
(1, 'Tovuq jigar', 'kg', 5, 8, TRUE),
(1, 'Tovuq yurak', 'kg', 3, 5, TRUE),
(1, 'Tovuq oshqozon (Jigarka)', 'kg', 3, 5, TRUE),
(1, 'Tovuq terisi', 'kg', 2, 3, TRUE),
(1, 'Tovuq suyagi (Bulyon uchun)', 'kg', 5, 8, TRUE),
-- Turkey (Kurka)
(1, 'Kurka (Butun)', 'dona', 3, 5, TRUE),
(1, 'Kurka ko''krak (Fillet)', 'kg', 5, 8, TRUE),
(1, 'Kurka son', 'kg', 5, 8, TRUE),
(1, 'Kurka qiyma', 'kg', 3, 5, TRUE),
-- Duck (O'rdak)
(1, 'O''rdak (Butun)', 'dona', 5, 8, TRUE),
(1, 'O''rdak ko''krak', 'kg', 3, 5, TRUE),
(1, 'O''rdak oyoq', 'kg', 2, 3, TRUE),
-- Quail (Bedana)
(1, 'Bedana (Butun)', 'dona', 20, 30, TRUE),
-- Other Poultry
(1, 'G''oz (Butun)', 'dona', 2, 3, TRUE),
(1, 'Kaklik', 'dona', 5, 8, TRUE),
-- Processed Meats
(1, 'Kolbasa (Doktorskaya)', 'kg', 5, 8, TRUE),
(1, 'Kolbasa (Servelat)', 'kg', 3, 5, TRUE),
(1, 'Kolbasa (Dudlangan)', 'kg', 3, 5, TRUE),
(1, 'Sosiska', 'kg', 5, 8, TRUE),
(1, 'Sardelka', 'kg', 3, 5, TRUE),
(1, 'Bekon', 'kg', 3, 5, TRUE),
(1, 'Jambon (Ham)', 'kg', 3, 5, TRUE),
(1, 'Pastirma (Qoq go''sht)', 'kg', 2, 3, TRUE),
(1, 'Basturma', 'kg', 2, 3, TRUE),
(1, 'Sudjuk', 'kg', 2, 3, TRUE),
-- Frozen Meats
(1, 'Muzlatilgan tovuq (Butun)', 'dona', 10, 15, TRUE),
(1, 'Muzlatilgan tovuq ko''krak', 'kg', 10, 15, TRUE),
(1, 'Muzlatilgan mol go''shti', 'kg', 10, 15, TRUE),
(1, 'Muzlatilgan qo''y go''shti', 'kg', 10, 15, TRUE),
-- Specialty
(1, 'Quyon go''shti', 'kg', 2, 3, TRUE),
(1, 'Kiyik go''shti', 'kg', 1, 2, TRUE),
(1, 'Yovvoyi cho''chqa', 'kg', 1, 2, TRUE),
(1, 'Ot go''shti (Qazi)', 'kg', 2, 3, TRUE);

-- ============================================================================
-- CATEGORY 4: FISH & SEAFOOD (Baliq va dengiz mahsulotlari) - 50 items
-- ============================================================================
INSERT INTO inventory_ingredients (restaurant_id, name, unit, minimum_stock, reorder_level, track_inventory) VALUES
-- Fresh Fish
(1, 'Sudak (Pike-perch)', 'kg', 5, 8, TRUE),
(1, 'Oq baliq (Whitefish)', 'kg', 5, 8, TRUE),
(1, 'Som baliq (Catfish)', 'kg', 5, 8, TRUE),
(1, 'Karp (Carp)', 'kg', 5, 8, TRUE),
(1, 'Forel (Trout)', 'kg', 3, 5, TRUE),
(1, 'Losos (Salmon)', 'kg', 5, 8, TRUE),
(1, 'Skabriya (Mackerel)', 'kg', 5, 8, TRUE),
(1, 'Sazan', 'kg', 5, 8, TRUE),
(1, 'Oqbalik (Sturgeon)', 'kg', 2, 3, TRUE),
(1, 'Baliq filesi (Oq)', 'kg', 10, 15, TRUE),
(1, 'Baliq filesi (Qizil)', 'kg', 5, 8, TRUE),
(1, 'Seld (Herring)', 'kg', 3, 5, TRUE),
(1, 'Sardina', 'kg', 2, 3, TRUE),
(1, 'Tuna (Fresh)', 'kg', 3, 5, TRUE),
(1, 'Dorado', 'kg', 3, 5, TRUE),
(1, 'Seabass', 'kg', 3, 5, TRUE),
(1, 'Tilapia', 'kg', 5, 8, TRUE),
-- Frozen Fish
(1, 'Muzlatilgan losos', 'kg', 5, 8, TRUE),
(1, 'Muzlatilgan forel', 'kg', 5, 8, TRUE),
(1, 'Muzlatilgan baliq filesi', 'kg', 10, 15, TRUE),
(1, 'Muzlatilgan tuna', 'kg', 3, 5, TRUE),
(1, 'Muzlatilgan skabriya', 'kg', 5, 8, TRUE),
-- Smoked & Preserved Fish
(1, 'Dudlangan losos', 'kg', 2, 3, TRUE),
(1, 'Dudlangan skabriya', 'kg', 2, 3, TRUE),
(1, 'Tuzlangan seld', 'kg', 3, 5, TRUE),
(1, 'Qoq baliq (Dried fish)', 'kg', 2, 3, TRUE),
(1, 'Baliq ikrasi (Qora)', 'kg', 0.5, 1, TRUE),
(1, 'Baliq ikrasi (Qizil)', 'kg', 1, 2, TRUE),
-- Seafood (Shellfish)
(1, 'Krevetka (Kichik)', 'kg', 3, 5, TRUE),
(1, 'Krevetka (Katta/Tiger)', 'kg', 2, 3, TRUE),
(1, 'Krevetka (King)', 'kg', 1, 2, TRUE),
(1, 'Kalamar', 'kg', 3, 5, TRUE),
(1, 'Osminog (Octopus)', 'kg', 1, 2, TRUE),
(1, 'Midiya (Mussels)', 'kg', 2, 3, TRUE),
(1, 'Ustritsa (Oysters)', 'dona', 20, 30, TRUE),
(1, 'Qisqichbaqa (Lobster)', 'kg', 1, 2, TRUE),
(1, 'Krab (Crab)', 'kg', 1, 2, TRUE),
(1, 'Krab tayoqchasi', 'kg', 3, 5, TRUE),
(1, 'Grebeshok (Scallops)', 'kg', 1, 2, TRUE),
-- Frozen Seafood
(1, 'Muzlatilgan krevetka', 'kg', 5, 8, TRUE),
(1, 'Muzlatilgan kalamar', 'kg', 3, 5, TRUE),
(1, 'Muzlatilgan midiya', 'kg', 2, 3, TRUE),
(1, 'Muzlatilgan dengiz kokteyli', 'kg', 2, 3, TRUE),
-- Canned Fish
(1, 'Konservalangan tuna', 'dona', 20, 30, TRUE),
(1, 'Konservalangan sardina', 'dona', 20, 30, TRUE),
(1, 'Konservalangan losos', 'dona', 10, 15, TRUE),
(1, 'Konservalangan seld', 'dona', 15, 20, TRUE),
(1, 'Shproti', 'dona', 15, 20, TRUE),
(1, 'Baliq pashtet', 'dona', 10, 15, TRUE);

-- ============================================================================
-- CATEGORY 5: DAIRY (Sut mahsulotlari) - 60 items
-- ============================================================================
INSERT INTO inventory_ingredients (restaurant_id, name, unit, minimum_stock, reorder_level, track_inventory) VALUES
-- Fresh Milk
(1, 'Sut (Yangi, 3.2%)', 'litr', 20, 30, TRUE),
(1, 'Sut (Yangi, 2.5%)', 'litr', 15, 25, TRUE),
(1, 'Sut (Yangi, 1%)', 'litr', 10, 15, TRUE),
(1, 'Sut (Pasterizatsiya, 3.2%)', 'litr', 20, 30, TRUE),
(1, 'Sut (Pasterizatsiya, 2.5%)', 'litr', 15, 25, TRUE),
(1, 'Sut (UHT)', 'litr', 30, 50, TRUE),
(1, 'Sut (Laktoziz)', 'litr', 5, 10, TRUE),
(1, 'Echki suti', 'litr', 5, 8, TRUE),
-- Cream
(1, 'Qaymoq (10%)', 'litr', 5, 8, TRUE),
(1, 'Qaymoq (20%)', 'litr', 5, 8, TRUE),
(1, 'Qaymoq (33%)', 'litr', 5, 8, TRUE),
(1, 'Qaymoq (Kofе uchun)', 'litr', 3, 5, TRUE),
(1, 'Smetana (15%)', 'kg', 5, 8, TRUE),
(1, 'Smetana (20%)', 'kg', 5, 8, TRUE),
(1, 'Smetana (25%)', 'kg', 3, 5, TRUE),
-- Butter
(1, 'Sariyog'' (82.5%)', 'kg', 10, 15, TRUE),
(1, 'Sariyog'' (72.5%)', 'kg', 8, 12, TRUE),
(1, 'Sariyog'' (Tuzli)', 'kg', 3, 5, TRUE),
(1, 'Sariyog'' (Tuzsiz)', 'kg', 5, 8, TRUE),
(1, 'Maslo toplenoe (Ghee)', 'kg', 3, 5, TRUE),
-- Cheese - Hard
(1, 'Pishloq (Rossiyskiy)', 'kg', 5, 8, TRUE),
(1, 'Pishloq (Gollandskiy)', 'kg', 3, 5, TRUE),
(1, 'Pishloq (Cheddar)', 'kg', 3, 5, TRUE),
(1, 'Pishloq (Parmesan)', 'kg', 2, 3, TRUE),
(1, 'Pishloq (Gouda)', 'kg', 2, 3, TRUE),
(1, 'Pishloq (Maasdam)', 'kg', 2, 3, TRUE),
(1, 'Pishloq (Emmental)', 'kg', 2, 3, TRUE),
-- Cheese - Soft/Fresh
(1, 'Tvorog (9%)', 'kg', 5, 8, TRUE),
(1, 'Tvorog (5%)', 'kg', 5, 8, TRUE),
(1, 'Tvorog (0%)', 'kg', 3, 5, TRUE),
(1, 'Pishloq (Mozzarella)', 'kg', 5, 8, TRUE),
(1, 'Pishloq (Feta)', 'kg', 3, 5, TRUE),
(1, 'Pishloq (Ricotta)', 'kg', 2, 3, TRUE),
(1, 'Pishloq (Mascarpone)', 'kg', 2, 3, TRUE),
(1, 'Pishloq (Cream cheese)', 'kg', 3, 5, TRUE),
(1, 'Pishloq (Brie)', 'kg', 1, 2, TRUE),
(1, 'Pishloq (Camembert)', 'kg', 1, 2, TRUE),
(1, 'Pishloq (Gorgonzola)', 'kg', 1, 2, TRUE),
(1, 'Pishloq (Roquefort)', 'kg', 1, 2, TRUE),
-- Processed Cheese
(1, 'Pishloq (Plavlenniy)', 'kg', 3, 5, TRUE),
(1, 'Pishloq (Slimsiz, eritilgan)', 'kg', 2, 3, TRUE),
-- Fermented Dairy
(1, 'Kefir (2.5%)', 'litr', 10, 15, TRUE),
(1, 'Kefir (1%)', 'litr', 5, 10, TRUE),
(1, 'Ryajenka', 'litr', 5, 8, TRUE),
(1, 'Prostokvasha', 'litr', 3, 5, TRUE),
(1, 'Qatiq', 'litr', 10, 15, TRUE),
(1, 'Ayran', 'litr', 10, 15, TRUE),
(1, 'Suzma', 'kg', 5, 8, TRUE),
(1, 'Chakka', 'kg', 3, 5, TRUE),
(1, 'Qurt', 'kg', 2, 3, TRUE),
-- Yogurt
(1, 'Yogurt (Tabiiy)', 'kg', 5, 8, TRUE),
(1, 'Yogurt (Mevali)', 'kg', 3, 5, TRUE),
(1, 'Yogurt (Grek)', 'kg', 3, 5, TRUE),
(1, 'Yogurt (Ichimlik)', 'litr', 5, 8, TRUE),
-- Condensed & Powdered
(1, 'Sut (Quritilgan)', 'kg', 5, 8, TRUE),
(1, 'Sut (Kondensatsiya, shirinli)', 'kg', 5, 8, TRUE),
(1, 'Sut (Kondensatsiya, shirinsiz)', 'kg', 3, 5, TRUE),
(1, 'Qaymoq (Quritilgan)', 'kg', 2, 3, TRUE),
-- Ice Cream Base
(1, 'Muzqaymoq bazasi', 'kg', 5, 8, TRUE);

-- ============================================================================
-- CATEGORY 6: RICE & GRAINS (Guruch va donlar) - 40 items
-- ============================================================================
INSERT INTO inventory_ingredients (restaurant_id, name, unit, minimum_stock, reorder_level, track_inventory) VALUES
-- Rice Varieties
(1, 'Guruch (Devzira)', 'kg', 30, 50, TRUE),
(1, 'Guruch (Lazar)', 'kg', 25, 40, TRUE),
(1, 'Guruch (Basmati)', 'kg', 15, 25, TRUE),
(1, 'Guruch (Jasmine)', 'kg', 10, 15, TRUE),
(1, 'Guruch (Arborio)', 'kg', 5, 10, TRUE),
(1, 'Guruch (Uzun donli)', 'kg', 20, 30, TRUE),
(1, 'Guruch (Qisqa donli)', 'kg', 10, 15, TRUE),
(1, 'Guruch (Qizil)', 'kg', 5, 8, TRUE),
(1, 'Guruch (Qora/Yovvoyi)', 'kg', 3, 5, TRUE),
(1, 'Guruch (Sushi uchun)', 'kg', 10, 15, TRUE),
(1, 'Guruch (Paella uchun)', 'kg', 5, 8, TRUE),
-- Wheat
(1, 'Bug''doy (Butun don)', 'kg', 10, 15, TRUE),
(1, 'Bug''doy (Maydalangan)', 'kg', 10, 15, TRUE),
(1, 'Bulgur (Yirik)', 'kg', 5, 10, TRUE),
(1, 'Bulgur (Mayda)', 'kg', 5, 10, TRUE),
(1, 'Kuskus', 'kg', 5, 8, TRUE),
(1, 'Yormа (Mannaya krupa)', 'kg', 5, 8, TRUE),
-- Buckwheat & Other Grains
(1, 'Grechixa (Buckwheat)', 'kg', 10, 15, TRUE),
(1, 'Grechixa (Yashil)', 'kg', 3, 5, TRUE),
(1, 'Arpa (Perlovka)', 'kg', 5, 10, TRUE),
(1, 'Arpa (Yachnevaya)', 'kg', 5, 8, TRUE),
(1, 'Jo''xori (Millet)', 'kg', 3, 5, TRUE),
(1, 'Suli (Oats)', 'kg', 5, 10, TRUE),
(1, 'Suli yormasi (Oatmeal)', 'kg', 10, 15, TRUE),
(1, 'Quinoa', 'kg', 3, 5, TRUE),
(1, 'Amarant', 'kg', 2, 3, TRUE),
-- Corn
(1, 'Makkajo''xori yormasi (Polenta)', 'kg', 5, 8, TRUE),
(1, 'Makkajo''xori krahmali', 'kg', 5, 10, TRUE),
(1, 'Makkajo''xori (Quritilgan)', 'kg', 5, 8, TRUE),
-- Pasta & Noodles
(1, 'Makaron (Spagetti)', 'kg', 10, 15, TRUE),
(1, 'Makaron (Penne)', 'kg', 8, 12, TRUE),
(1, 'Makaron (Farfalle)', 'kg', 5, 8, TRUE),
(1, 'Makaron (Fusilli)', 'kg', 5, 8, TRUE),
(1, 'Makaron (Tagliatelle)', 'kg', 5, 8, TRUE),
(1, 'Makaron (Lasagne listlari)', 'kg', 3, 5, TRUE),
(1, 'Ugra (Homemade noodles)', 'kg', 5, 10, TRUE),
(1, 'Funchoza (Glass noodles)', 'kg', 3, 5, TRUE),
(1, 'Udon', 'kg', 3, 5, TRUE),
(1, 'Ramen noodles', 'kg', 3, 5, TRUE),
(1, 'Soba noodles', 'kg', 2, 3, TRUE);

-- ============================================================================
-- CATEGORY 7: FLOUR & BAKERY (Un va non mahsulotlari) - 50 items
-- ============================================================================
INSERT INTO inventory_ingredients (restaurant_id, name, unit, minimum_stock, reorder_level, track_inventory) VALUES
-- Flour Types
(1, 'Un (Yuqori nav)', 'kg', 50, 80, TRUE),
(1, 'Un (1-nav)', 'kg', 30, 50, TRUE),
(1, 'Un (2-nav)', 'kg', 20, 30, TRUE),
(1, 'Un (Butun donli)', 'kg', 10, 15, TRUE),
(1, 'Un (Javdar/Rye)', 'kg', 5, 10, TRUE),
(1, 'Un (Makkajo''xori)', 'kg', 5, 8, TRUE),
(1, 'Un (Guruch)', 'kg', 5, 8, TRUE),
(1, 'Un (Suli)', 'kg', 3, 5, TRUE),
(1, 'Un (No''xat)', 'kg', 3, 5, TRUE),
(1, 'Un (Bodom)', 'kg', 2, 3, TRUE),
(1, 'Un (Kokos)', 'kg', 2, 3, TRUE),
(1, 'Un (Semolina)', 'kg', 5, 8, TRUE),
-- Bread - Fresh
(1, 'Non (Obi non)', 'dona', 30, 50, TRUE),
(1, 'Non (Patir)', 'dona', 20, 30, TRUE),
(1, 'Non (Tandircha)', 'dona', 20, 30, TRUE),
(1, 'Non (Kulcha)', 'dona', 15, 25, TRUE),
(1, 'Non (Lavash)', 'dona', 20, 30, TRUE),
(1, 'Non (Oq, narezanniy)', 'dona', 15, 25, TRUE),
(1, 'Non (Qora)', 'dona', 10, 15, TRUE),
(1, 'Non (Borodinsky)', 'dona', 5, 10, TRUE),
(1, 'Non (Baget)', 'dona', 10, 15, TRUE),
(1, 'Non (Ciabatta)', 'dona', 8, 12, TRUE),
(1, 'Non (Focaccia)', 'dona', 5, 8, TRUE),
(1, 'Non (Burger uchun)', 'dona', 30, 50, TRUE),
(1, 'Non (Hotdog uchun)', 'dona', 20, 30, TRUE),
(1, 'Non (Pita)', 'dona', 20, 30, TRUE),
(1, 'Non (Tortilla)', 'dona', 30, 50, TRUE),
-- Baked Goods - Sweet
(1, 'Somsa', 'dona', 50, 80, TRUE),
(1, 'Samsa (Go''shtli)', 'dona', 30, 50, TRUE),
(1, 'Samsa (Qovoqli)', 'dona', 20, 30, TRUE),
(1, 'Pirojki (Go''shtli)', 'dona', 20, 30, TRUE),
(1, 'Pirojki (Kartoshkali)', 'dona', 15, 25, TRUE),
(1, 'Pirojki (Karamli)', 'dona', 15, 25, TRUE),
(1, 'Bulochka (Oddiy)', 'dona', 20, 30, TRUE),
(1, 'Bulochka (Mayizli)', 'dona', 15, 25, TRUE),
(1, 'Bulochka (Shokoladli)', 'dona', 15, 25, TRUE),
(1, 'Kruassan', 'dona', 20, 30, TRUE),
-- Crackers & Breadcrumbs
(1, 'Suhari (Breadcrumbs)', 'kg', 5, 10, TRUE),
(1, 'Suhari (Panko)', 'kg', 3, 5, TRUE),
(1, 'Krekker (Tuzli)', 'kg', 3, 5, TRUE),
(1, 'Krekker (Susamli)', 'kg', 2, 3, TRUE),
(1, 'Xlebsy (Crispbread)', 'kg', 2, 3, TRUE),
-- Dough & Pastry
(1, 'Xamir (Tayyor, slo''enoe)', 'kg', 5, 10, TRUE),
(1, 'Xamir (Tayyor, pesochnoe)', 'kg', 3, 5, TRUE),
(1, 'Xamir (Tayyor, filo)', 'kg', 2, 3, TRUE),
(1, 'Xamir (Tayyor, pizza uchun)', 'kg', 5, 8, TRUE),
-- Baking Ingredients
(1, 'Xamirturush (Yeast, quruq)', 'kg', 2, 3, TRUE),
(1, 'Xamirturush (Yeast, yangi)', 'kg', 1, 2, TRUE),
(1, 'Soda (Baking soda)', 'kg', 2, 3, TRUE),
(1, 'Ko''pirtiruvchi (Baking powder)', 'kg', 2, 3, TRUE);

-- ============================================================================
-- CATEGORY 8: SPICES & SEASONINGS (Ziravorlar) - 80 items
-- ============================================================================
INSERT INTO inventory_ingredients (restaurant_id, name, unit, minimum_stock, reorder_level, track_inventory) VALUES
-- Basic Spices
(1, 'Tuz (Osh tuzi)', 'kg', 20, 30, TRUE),
(1, 'Tuz (Dengiz tuzi)', 'kg', 5, 10, TRUE),
(1, 'Tuz (Himalay pinki)', 'kg', 2, 3, TRUE),
(1, 'Qora murch (Maydalangan)', 'kg', 3, 5, TRUE),
(1, 'Qora murch (Butun)', 'kg', 2, 3, TRUE),
(1, 'Oq murch', 'kg', 1, 2, TRUE),
(1, 'Qizil murch (Maydalangan)', 'kg', 2, 3, TRUE),
(1, 'Qizil murch (Butun)', 'kg', 1, 2, TRUE),
(1, 'Paprika (Shirin)', 'kg', 2, 3, TRUE),
(1, 'Paprika (Achchiq)', 'kg', 1, 2, TRUE),
(1, 'Paprika (Dudlangan)', 'kg', 1, 2, TRUE),
-- Central Asian Spices
(1, 'Zira (Cumin)', 'kg', 3, 5, TRUE),
(1, 'Zira (Butun)', 'kg', 2, 3, TRUE),
(1, 'Kashnich (Coriander, maydalangan)', 'kg', 2, 3, TRUE),
(1, 'Kashnich (Coriander, butun)', 'kg', 2, 3, TRUE),
(1, 'Zanjabil (Quruq)', 'kg', 1, 2, TRUE),
(1, 'Zarchava (Turmeric)', 'kg', 2, 3, TRUE),
(1, 'Kok choy (Fenugreek)', 'kg', 1, 2, TRUE),
(1, 'Arpa bodiyon (Fennel seeds)', 'kg', 1, 2, TRUE),
(1, 'Oq bodiyon (Anise)', 'kg', 1, 2, TRUE),
-- Aromatic Spices
(1, 'Dolchin (Cinnamon, maydalangan)', 'kg', 1, 2, TRUE),
(1, 'Dolchin (Cinnamon, tayoqcha)', 'kg', 0.5, 1, TRUE),
(1, 'Mixak (Cloves)', 'kg', 0.5, 1, TRUE),
(1, 'Muskat yong''og''i (Nutmeg)', 'kg', 0.5, 1, TRUE),
(1, 'Kardamon (Yashil)', 'kg', 0.5, 1, TRUE),
(1, 'Kardamon (Qora)', 'kg', 0.3, 0.5, TRUE),
(1, 'Yulduz bodiyon (Star anise)', 'kg', 0.5, 1, TRUE),
(1, 'Safron (Saffron)', 'gr', 10, 20, TRUE),
(1, 'Vanilla (Quruq)', 'gr', 20, 30, TRUE),
(1, 'Vanilla ekstrakti', 'litr', 0.5, 1, TRUE),
-- Herbs (Dried)
(1, 'Ukrop (Quruq)', 'kg', 1, 2, TRUE),
(1, 'Petrushka (Quruq)', 'kg', 1, 2, TRUE),
(1, 'Rayxon (Quruq)', 'kg', 1, 2, TRUE),
(1, 'Oregano', 'kg', 1, 2, TRUE),
(1, 'Timo''n (Thyme)', 'kg', 1, 2, TRUE),
(1, 'Rozmarin (Rosemary)', 'kg', 0.5, 1, TRUE),
(1, 'Lavr yaprogi (Bay leaf)', 'kg', 1, 2, TRUE),
(1, 'Marjoram', 'kg', 0.5, 1, TRUE),
(1, 'Shalfeу (Sage)', 'kg', 0.5, 1, TRUE),
(1, 'Tarxun (Tarragon)', 'kg', 0.5, 1, TRUE),
(1, 'Yalpiz (Mint, quruq)', 'kg', 1, 2, TRUE),
(1, 'Chashni (Savory)', 'kg', 0.5, 1, TRUE),
-- Spice Blends
(1, 'Kari (Curry powder)', 'kg', 1, 2, TRUE),
(1, 'Garam masala', 'kg', 1, 2, TRUE),
(1, 'Hmel-suneli', 'kg', 1, 2, TRUE),
(1, 'Adjika (Quruq)', 'kg', 0.5, 1, TRUE),
(1, 'Baharat', 'kg', 0.5, 1, TRUE),
(1, 'Ras el hanout', 'kg', 0.5, 1, TRUE),
(1, 'Za''atar', 'kg', 0.5, 1, TRUE),
(1, 'Sumac', 'kg', 0.5, 1, TRUE),
(1, 'Berbere', 'kg', 0.3, 0.5, TRUE),
(1, 'Jerk seasoning', 'kg', 0.3, 0.5, TRUE),
(1, 'Old bay seasoning', 'kg', 0.3, 0.5, TRUE),
(1, 'Taco seasoning', 'kg', 0.5, 1, TRUE),
(1, 'Italian seasoning', 'kg', 0.5, 1, TRUE),
(1, 'Provence otlari', 'kg', 0.5, 1, TRUE),
-- Pepper Varieties
(1, 'Sichuan murch', 'kg', 0.3, 0.5, TRUE),
(1, 'Cayenne murch', 'kg', 0.5, 1, TRUE),
(1, 'Chipotle murch (Quruq)', 'kg', 0.3, 0.5, TRUE),
(1, 'Habanero murch (Quruq)', 'kg', 0.2, 0.3, TRUE),
(1, 'Jalapeno murch (Quruq)', 'kg', 0.3, 0.5, TRUE),
-- Seeds
(1, 'Sezam (Oq)', 'kg', 2, 3, TRUE),
(1, 'Sezam (Qora)', 'kg', 1, 2, TRUE),
(1, 'Xashxash (Poppy seeds)', 'kg', 1, 2, TRUE),
(1, 'Zig''ir (Flax seeds)', 'kg', 1, 2, TRUE),
(1, 'Xardal urug''i (Mustard seeds)', 'kg', 1, 2, TRUE),
(1, 'Karom (Ajwain)', 'kg', 0.5, 1, TRUE),
(1, 'Nigella (Qora zira)', 'kg', 0.5, 1, TRUE),
-- Garlic & Onion (Dried)
(1, 'Sarimsoq (Quruq, granula)', 'kg', 2, 3, TRUE),
(1, 'Sarimsoq (Quruq, kukunli)', 'kg', 2, 3, TRUE),
(1, 'Piyoz (Quruq, granula)', 'kg', 2, 3, TRUE),
(1, 'Piyoz (Quruq, kukunli)', 'kg', 2, 3, TRUE),
-- MSG & Flavor Enhancers
(1, 'Glutamat natriy (MSG)', 'kg', 1, 2, TRUE),
(1, 'Bulyon kubiklari (Tovuq)', 'dona', 100, 150, TRUE),
(1, 'Bulyon kubiklari (Mol)', 'dona', 100, 150, TRUE),
(1, 'Bulyon kubiklari (Sabzavot)', 'dona', 50, 80, TRUE),
-- Specialty
(1, 'Wasabi (Kukunli)', 'kg', 0.3, 0.5, TRUE),
(1, 'Kuzumaki (Horseradish)', 'kg', 0.5, 1, TRUE);

-- ============================================================================
-- CATEGORY 9: OILS & FATS (Moylar va yog'lar) - 35 items
-- ============================================================================
INSERT INTO inventory_ingredients (restaurant_id, name, unit, minimum_stock, reorder_level, track_inventory) VALUES
-- Cooking Oils
(1, 'Paxta yog''i', 'litr', 30, 50, TRUE),
(1, 'Kungaboqar yog''i (Rafinatsiya)', 'litr', 30, 50, TRUE),
(1, 'Kungaboqar yog''i (Tabiiy)', 'litr', 10, 15, TRUE),
(1, 'Makkajo''xori yog''i', 'litr', 10, 15, TRUE),
(1, 'Soya yog''i', 'litr', 10, 15, TRUE),
(1, 'Rapsoil (Canola)', 'litr', 10, 15, TRUE),
(1, 'Yer yong''oq yog''i (Peanut oil)', 'litr', 5, 10, TRUE),
-- Premium Oils
(1, 'Zaytun yog''i (Extra virgin)', 'litr', 10, 15, TRUE),
(1, 'Zaytun yog''i (Light)', 'litr', 5, 10, TRUE),
(1, 'Zaytun yog''i (Pomace)', 'litr', 5, 8, TRUE),
(1, 'Sezam yog''i', 'litr', 3, 5, TRUE),
(1, 'Zig''ir yog''i (Linseed)', 'litr', 2, 3, TRUE),
(1, 'Yong''oq yog''i (Walnut)', 'litr', 1, 2, TRUE),
(1, 'Avokado yog''i', 'litr', 2, 3, TRUE),
(1, 'Uzum urug''i yog''i', 'litr', 2, 3, TRUE),
(1, 'Kokos yog''i', 'litr', 3, 5, TRUE),
(1, 'Truffle oil', 'litr', 0.5, 1, TRUE),
-- Animal Fats
(1, 'Cho''chqa yog''i (Salo)', 'kg', 3, 5, TRUE),
(1, 'Mol yog''i (Rendered)', 'kg', 3, 5, TRUE),
(1, 'O''rdak yog''i', 'kg', 1, 2, TRUE),
(1, 'G''oz yog''i', 'kg', 1, 2, TRUE),
(1, 'Qo''y yog''i (Dumba)', 'kg', 5, 8, TRUE),
-- Margarine & Spreads
(1, 'Margarin', 'kg', 5, 10, TRUE),
(1, 'Spred (Butter substitute)', 'kg', 3, 5, TRUE),
-- Specialty Fats
(1, 'Kakao yog''i', 'kg', 1, 2, TRUE),
(1, 'Palma yog''i', 'kg', 5, 10, TRUE),
(1, 'Shortening', 'kg', 3, 5, TRUE),
-- Vinegars
(1, 'Sirka (Oq, 9%)', 'litr', 10, 15, TRUE),
(1, 'Sirka (Olma)', 'litr', 5, 8, TRUE),
(1, 'Sirka (Uzum/Wine)', 'litr', 3, 5, TRUE),
(1, 'Sirka (Balsamic)', 'litr', 2, 3, TRUE),
(1, 'Sirka (Guruch)', 'litr', 2, 3, TRUE),
(1, 'Sirka (Sherry)', 'litr', 1, 2, TRUE),
(1, 'Limon suvи (Bottled)', 'litr', 3, 5, TRUE),
(1, 'Laym suvi (Bottled)', 'litr', 2, 3, TRUE);

-- ============================================================================
-- CATEGORY 10: BEVERAGES (Ichimliklar) - 60 items
-- ============================================================================
INSERT INTO inventory_ingredients (restaurant_id, name, unit, minimum_stock, reorder_level, track_inventory) VALUES
-- Water
(1, 'Suv (Ichimlik, gazlangan)', 'litr', 50, 80, TRUE),
(1, 'Suv (Ichimlik, gazsiz)', 'litr', 50, 80, TRUE),
(1, 'Suv (Mineral, gazlangan)', 'litr', 30, 50, TRUE),
(1, 'Suv (Mineral, gazsiz)', 'litr', 30, 50, TRUE),
-- Juices - Fresh
(1, 'Sharbat (Apelsin, fresh)', 'litr', 10, 15, TRUE),
(1, 'Sharbat (Olma, fresh)', 'litr', 10, 15, TRUE),
(1, 'Sharbat (Sabzi, fresh)', 'litr', 5, 8, TRUE),
(1, 'Sharbat (Pomidor, fresh)', 'litr', 5, 8, TRUE),
(1, 'Sharbat (Anor, fresh)', 'litr', 5, 8, TRUE),
(1, 'Sharbat (Uzum, fresh)', 'litr', 5, 8, TRUE),
-- Juices - Packaged
(1, 'Sharbat (Apelsin, paket)', 'litr', 20, 30, TRUE),
(1, 'Sharbat (Olma, paket)', 'litr', 20, 30, TRUE),
(1, 'Sharbat (Ananas, paket)', 'litr', 10, 15, TRUE),
(1, 'Sharbat (Mango, paket)', 'litr', 10, 15, TRUE),
(1, 'Sharbat (Multifruit, paket)', 'litr', 15, 20, TRUE),
(1, 'Sharbat (Pomidor, paket)', 'litr', 10, 15, TRUE),
(1, 'Sharbat (Anor, paket)', 'litr', 10, 15, TRUE),
(1, 'Nektar (Shaftoli)', 'litr', 10, 15, TRUE),
(1, 'Nektar (O''rik)', 'litr', 10, 15, TRUE),
-- Carbonated Drinks
(1, 'Coca-Cola', 'litr', 30, 50, TRUE),
(1, 'Coca-Cola Zero', 'litr', 10, 15, TRUE),
(1, 'Pepsi', 'litr', 20, 30, TRUE),
(1, 'Fanta', 'litr', 20, 30, TRUE),
(1, 'Sprite', 'litr', 20, 30, TRUE),
(1, '7UP', 'litr', 15, 25, TRUE),
(1, 'Schweppes (Tonic)', 'litr', 10, 15, TRUE),
(1, 'Schweppes (Ginger ale)', 'litr', 5, 10, TRUE),
-- Compote & Fruit Drinks
(1, 'Kompot (O''rik)', 'litr', 10, 15, TRUE),
(1, 'Kompot (Olcha)', 'litr', 10, 15, TRUE),
(1, 'Kompot (Olma)', 'litr', 10, 15, TRUE),
(1, 'Kompot (Aralash)', 'litr', 10, 15, TRUE),
(1, 'Mors (Cranberry)', 'litr', 5, 8, TRUE),
(1, 'Mors (Lingonberry)', 'litr', 5, 8, TRUE),
-- Kvass & Traditional
(1, 'Kvas', 'litr', 20, 30, TRUE),
(1, 'Chalob', 'litr', 10, 15, TRUE),
-- Energy & Sports
(1, 'Red Bull', 'dona', 30, 50, TRUE),
(1, 'Monster Energy', 'dona', 20, 30, TRUE),
(1, 'Gatorade', 'litr', 10, 15, TRUE),
-- Tea & Coffee (RTD)
(1, 'Ice Tea (Limon)', 'litr', 15, 25, TRUE),
(1, 'Ice Tea (Shaftoli)', 'litr', 15, 25, TRUE),
(1, 'Ice Coffee', 'litr', 10, 15, TRUE),
-- Syrups
(1, 'Sirop (Qulupnay)', 'litr', 3, 5, TRUE),
(1, 'Sirop (Malina)', 'litr', 3, 5, TRUE),
(1, 'Sirop (Limon)', 'litr', 3, 5, TRUE),
(1, 'Sirop (Grenadine)', 'litr', 2, 3, TRUE),
(1, 'Sirop (Karamel)', 'litr', 3, 5, TRUE),
(1, 'Sirop (Vanilla)', 'litr', 3, 5, TRUE),
(1, 'Sirop (Xazelnut)', 'litr', 2, 3, TRUE),
(1, 'Sirop (Yalpiz)', 'litr', 2, 3, TRUE),
-- Concentrates
(1, 'Limonad kontsentrati', 'litr', 5, 10, TRUE),
(1, 'Meva kontsentrati (Apelsin)', 'litr', 3, 5, TRUE),
(1, 'Meva kontsentrati (Olma)', 'litr', 3, 5, TRUE),
-- Non-Alcoholic Beer/Wine
(1, 'Pivo (Alkogolsiz)', 'litr', 10, 15, TRUE),
(1, 'Shampan (Alkogolsiz)', 'litr', 5, 8, TRUE),
-- Milk-Based Drinks
(1, 'Milkshake bazasi (Shokolad)', 'kg', 3, 5, TRUE),
(1, 'Milkshake bazasi (Vanilla)', 'kg', 3, 5, TRUE),
(1, 'Milkshake bazasi (Qulupnay)', 'kg', 2, 3, TRUE),
-- Plant-Based Milk
(1, 'Sut (Bodom)', 'litr', 5, 10, TRUE),
(1, 'Sut (Soya)', 'litr', 5, 10, TRUE),
(1, 'Sut (Kokos)', 'litr', 3, 5, TRUE),
(1, 'Sut (Suli)', 'litr', 3, 5, TRUE);

-- ============================================================================
-- CATEGORY 11: CANNED GOODS (Konservalar) - 50 items
-- ============================================================================
INSERT INTO inventory_ingredients (restaurant_id, name, unit, minimum_stock, reorder_level, track_inventory) VALUES
-- Canned Vegetables
(1, 'Pomidor (Konserva, butun)', 'dona', 30, 50, TRUE),
(1, 'Pomidor (Konserva, maydalangan)', 'dona', 30, 50, TRUE),
(1, 'Pomidor pastasi', 'dona', 50, 80, TRUE),
(1, 'Pomidor pyuresi', 'dona', 30, 50, TRUE),
(1, 'Bodring (Tuzlangan)', 'dona', 30, 50, TRUE),
(1, 'Pomidor (Tuzlangan)', 'dona', 20, 30, TRUE),
(1, 'Karam (Tuzlangan)', 'dona', 20, 30, TRUE),
(1, 'Makkajo''xori (Konserva)', 'dona', 30, 50, TRUE),
(1, 'No''xat (Konserva)', 'dona', 30, 50, TRUE),
(1, 'Loviya (Konserva, oq)', 'dona', 20, 30, TRUE),
(1, 'Loviya (Konserva, qizil)', 'dona', 20, 30, TRUE),
(1, 'Loviya (Konserva, yashil)', 'dona', 20, 30, TRUE),
(1, 'Qalampir (Konserva, qizil)', 'dona', 15, 25, TRUE),
(1, 'Zaytun (Yashil)', 'dona', 20, 30, TRUE),
(1, 'Zaytun (Qora)', 'dona', 20, 30, TRUE),
(1, 'Kaperslar', 'dona', 10, 15, TRUE),
(1, 'Artishok (Konserva)', 'dona', 10, 15, TRUE),
(1, 'Jalapeno (Konserva)', 'dona', 15, 25, TRUE),
(1, 'Bambuk novdalari (Konserva)', 'dona', 10, 15, TRUE),
(1, 'Suv chestnuti (Konserva)', 'dona', 10, 15, TRUE),
-- Canned Fruits
(1, 'Ananas (Konserva, halqali)', 'dona', 20, 30, TRUE),
(1, 'Ananas (Konserva, kubik)', 'dona', 15, 25, TRUE),
(1, 'Shaftoli (Konserva)', 'dona', 15, 25, TRUE),
(1, 'Nok (Konserva)', 'dona', 10, 15, TRUE),
(1, 'Olcha (Konserva)', 'dona', 10, 15, TRUE),
(1, 'Mango (Konserva)', 'dona', 10, 15, TRUE),
(1, 'Mandarin (Konserva)', 'dona', 15, 25, TRUE),
(1, 'Aralash mevalar (Konserva)', 'dona', 15, 25, TRUE),
-- Canned Legumes
(1, 'Loviya bilan pomidor (Konserva)', 'dona', 15, 25, TRUE),
(1, 'Hummus (Tayyor)', 'dona', 10, 15, TRUE),
(1, 'No''xat (Kabuli, konserva)', 'dona', 15, 25, TRUE),
-- Canned Soups & Broths
(1, 'Bulyon (Tovuq, konserva)', 'litr', 10, 15, TRUE),
(1, 'Bulyon (Mol, konserva)', 'litr', 10, 15, TRUE),
(1, 'Bulyon (Sabzavot, konserva)', 'litr', 10, 15, TRUE),
(1, 'Sho''rva (Tayyor, konserva)', 'dona', 10, 15, TRUE),
-- Canned Milk Products
(1, 'Sut (Kondensatsiyalangan)', 'dona', 30, 50, TRUE),
(1, 'Sut (Quritilgan, konserva)', 'dona', 20, 30, TRUE),
(1, 'Qaymoq (Konserva)', 'dona', 15, 25, TRUE),
(1, 'Kokos suti (Konserva)', 'dona', 20, 30, TRUE),
(1, 'Kokos qaymoq (Konserva)', 'dona', 10, 15, TRUE),
-- Pickled & Marinated
(1, 'Qo''ziqorin (Marinad)', 'dona', 20, 30, TRUE),
(1, 'Sarimsoq (Marinad)', 'dona', 15, 25, TRUE),
(1, 'Sabzavot assortisi (Marinad)', 'dona', 15, 25, TRUE),
(1, 'Lavash (Marinad)', 'dona', 10, 15, TRUE),
(1, 'Seldereу (Marinad)', 'dona', 10, 15, TRUE),
-- Specialty Canned
(1, 'Dolma bargi (Konserva)', 'dona', 10, 15, TRUE),
(1, 'Baklajan ikrasi', 'dona', 15, 25, TRUE),
(1, 'Kabachok ikrasi', 'dona', 15, 25, TRUE),
(1, 'Lecho', 'dona', 20, 30, TRUE),
(1, 'Ajika (Konserva)', 'dona', 15, 25, TRUE);

-- ============================================================================
-- CATEGORY 12: DRIED FOODS (Quritilgan mahsulotlar) - 40 items
-- ============================================================================
INSERT INTO inventory_ingredients (restaurant_id, name, unit, minimum_stock, reorder_level, track_inventory) VALUES
-- Dried Fruits
(1, 'Mayiz (Oq)', 'kg', 5, 10, TRUE),
(1, 'Mayiz (Qora)', 'kg', 5, 10, TRUE),
(1, 'Mayiz (Sultana)', 'kg', 3, 5, TRUE),
(1, 'O''rik (Quritilgan)', 'kg', 5, 8, TRUE),
(1, 'Olcha (Quritilgan)', 'kg', 3, 5, TRUE),
(1, 'Olxo''ri (Quritilgan/Chernosliv)', 'kg', 5, 8, TRUE),
(1, 'Anjir (Quritilgan)', 'kg', 3, 5, TRUE),
(1, 'Hurmo (Quritilgan)', 'kg', 5, 8, TRUE),
(1, 'Olma (Quritilgan)', 'kg', 2, 3, TRUE),
(1, 'Nok (Quritilgan)', 'kg', 2, 3, TRUE),
(1, 'Banan (Quritilgan)', 'kg', 2, 3, TRUE),
(1, 'Mango (Quritilgan)', 'kg', 2, 3, TRUE),
(1, 'Ananas (Quritilgan)', 'kg', 2, 3, TRUE),
(1, 'Papaya (Quritilgan)', 'kg', 1, 2, TRUE),
(1, 'Cranberry (Quritilgan)', 'kg', 2, 3, TRUE),
(1, 'Ko''k malina (Quritilgan)', 'kg', 1, 2, TRUE),
(1, 'Goji berries', 'kg', 1, 2, TRUE),
(1, 'Aralash quritilgan mevalar', 'kg', 3, 5, TRUE),
-- Dried Vegetables
(1, 'Pomidor (Quritilgan)', 'kg', 3, 5, TRUE),
(1, 'Qalampir (Quritilgan)', 'kg', 2, 3, TRUE),
(1, 'Qo''ziqorin (Quritilgan)', 'kg', 2, 3, TRUE),
(1, 'Piyoz (Quritilgan, qiyilgan)', 'kg', 3, 5, TRUE),
(1, 'Sarimsoq (Quritilgan)', 'kg', 2, 3, TRUE),
(1, 'Sabzi (Quritilgan)', 'kg', 2, 3, TRUE),
(1, 'Karam (Quritilgan)', 'kg', 1, 2, TRUE),
(1, 'Ko''katlar aralashmasi (Quritilgan)', 'kg', 2, 3, TRUE),
-- Dried Seaweed
(1, 'Nori', 'dona', 50, 80, TRUE),
(1, 'Wakame (Quritilgan)', 'kg', 1, 2, TRUE),
(1, 'Kombu', 'kg', 0.5, 1, TRUE),
(1, 'Agar-agar', 'kg', 0.5, 1, TRUE),
-- Other Dried
(1, 'Tofu (Quritilgan)', 'kg', 1, 2, TRUE),
(1, 'Yuba (Tofu terisi)', 'kg', 0.5, 1, TRUE),
(1, 'Shiitake (Quritilgan)', 'kg', 1, 2, TRUE),
(1, 'Porcini (Quritilgan)', 'kg', 0.5, 1, TRUE),
-- Fruit Powders
(1, 'Limon kukuni', 'kg', 0.5, 1, TRUE),
(1, 'Apelsin kukuni', 'kg', 0.5, 1, TRUE),
(1, 'Qulupnay kukuni', 'kg', 0.5, 1, TRUE),
(1, 'Malina kukuni', 'kg', 0.5, 1, TRUE),
(1, 'Mango kukuni', 'kg', 0.5, 1, TRUE),
(1, 'Banan kukuni', 'kg', 0.5, 1, TRUE);

-- ============================================================================
-- CATEGORY 13: NUTS & SEEDS (Yong'oqlar va urug'lar) - 35 items
-- ============================================================================
INSERT INTO inventory_ingredients (restaurant_id, name, unit, minimum_stock, reorder_level, track_inventory) VALUES
-- Tree Nuts
(1, 'Yong''oq (Grek)', 'kg', 5, 10, TRUE),
(1, 'Bodom', 'kg', 5, 8, TRUE),
(1, 'Bodom (Po''stloqsiz)', 'kg', 3, 5, TRUE),
(1, 'Bodom (Maydalangan)', 'kg', 2, 3, TRUE),
(1, 'Bodom (Platkalar)', 'kg', 2, 3, TRUE),
(1, 'Keshyu', 'kg', 3, 5, TRUE),
(1, 'Pistacho', 'kg', 3, 5, TRUE),
(1, 'Pistacho (Po''stloqsiz)', 'kg', 2, 3, TRUE),
(1, 'Funduk (Hazelnut)', 'kg', 3, 5, TRUE),
(1, 'Funduk (Po''stloqsiz)', 'kg', 2, 3, TRUE),
(1, 'Pekan', 'kg', 2, 3, TRUE),
(1, 'Makadamiya', 'kg', 1, 2, TRUE),
(1, 'Braziliya yong''og''i', 'kg', 1, 2, TRUE),
(1, 'Kashtan (Chestnut)', 'kg', 2, 3, TRUE),
(1, 'Qoraqand (Pine nuts)', 'kg', 2, 3, TRUE),
-- Ground Nuts
(1, 'Yer yong''oq (Peanut)', 'kg', 5, 10, TRUE),
(1, 'Yer yong''oq (Po''stloqsiz)', 'kg', 3, 5, TRUE),
(1, 'Yer yong''oq (Qovurilgan)', 'kg', 3, 5, TRUE),
-- Seeds
(1, 'Kungaboqar urug''i', 'kg', 5, 8, TRUE),
(1, 'Kungaboqar urug''i (Po''stloqsiz)', 'kg', 3, 5, TRUE),
(1, 'Qovoq urug''i', 'kg', 3, 5, TRUE),
(1, 'Qovoq urug''i (Po''stloqsiz)', 'kg', 2, 3, TRUE),
(1, 'Chia urug''i', 'kg', 2, 3, TRUE),
(1, 'Zig''ir urug''i (Flax)', 'kg', 2, 3, TRUE),
(1, 'Sezam urug''i (Oq)', 'kg', 2, 3, TRUE),
(1, 'Sezam urug''i (Qora)', 'kg', 1, 2, TRUE),
(1, 'Xashxash urug''i', 'kg', 1, 2, TRUE),
(1, 'Hemp seeds', 'kg', 1, 2, TRUE),
-- Nut Butters & Pastes
(1, 'Yer yong''oq pastasi (Peanut butter)', 'kg', 3, 5, TRUE),
(1, 'Bodom pastasi', 'kg', 2, 3, TRUE),
(1, 'Keshyu pastasi', 'kg', 1, 2, TRUE),
(1, 'Tahini (Sezam pastasi)', 'kg', 3, 5, TRUE),
(1, 'Xazelnut pastasi (Nutella tipidagi)', 'kg', 3, 5, TRUE),
-- Mixed & Roasted
(1, 'Aralash yong''oqlar', 'kg', 3, 5, TRUE),
(1, 'Trail mix (Yong''oq va meva)', 'kg', 2, 3, TRUE);

-- ============================================================================
-- CATEGORY 14: LEGUMES (Dukkaklilar) - 30 items
-- ============================================================================
INSERT INTO inventory_ingredients (restaurant_id, name, unit, minimum_stock, reorder_level, track_inventory) VALUES
-- Beans
(1, 'Loviya (Oq)', 'kg', 10, 15, TRUE),
(1, 'Loviya (Qizil)', 'kg', 8, 12, TRUE),
(1, 'Loviya (Qora)', 'kg', 5, 8, TRUE),
(1, 'Loviya (Pinto)', 'kg', 3, 5, TRUE),
(1, 'Loviya (Navy)', 'kg', 3, 5, TRUE),
(1, 'Loviya (Cannellini)', 'kg', 3, 5, TRUE),
(1, 'Loviya (Lima)', 'kg', 3, 5, TRUE),
(1, 'Mosh (Mung beans)', 'kg', 10, 15, TRUE),
(1, 'Adzuki beans', 'kg', 2, 3, TRUE),
-- Lentils
(1, 'Yasmiq (Qizil)', 'kg', 8, 12, TRUE),
(1, 'Yasmiq (Yashil)', 'kg', 5, 8, TRUE),
(1, 'Yasmiq (Jigarrang/Brown)', 'kg', 5, 8, TRUE),
(1, 'Yasmiq (Qora/Beluga)', 'kg', 2, 3, TRUE),
(1, 'Yasmiq (French/Puy)', 'kg', 2, 3, TRUE),
-- Peas
(1, 'No''xat (Kabuli/Chickpeas)', 'kg', 10, 15, TRUE),
(1, 'No''xat (Desi)', 'kg', 5, 8, TRUE),
(1, 'No''xat (Split peas, sariq)', 'kg', 5, 8, TRUE),
(1, 'No''xat (Split peas, yashil)', 'kg', 5, 8, TRUE),
(1, 'No''xat (Butun, quruq)', 'kg', 5, 8, TRUE),
-- Soybeans
(1, 'Soya (Butun)', 'kg', 5, 8, TRUE),
(1, 'Edamame (Muzlatilgan)', 'kg', 3, 5, TRUE),
(1, 'Tofu (Firm)', 'kg', 5, 8, TRUE),
(1, 'Tofu (Silken)', 'kg', 3, 5, TRUE),
(1, 'Tempeh', 'kg', 2, 3, TRUE),
-- Peanuts (Legume category)
(1, 'Yer yong''oq (Xom)', 'kg', 5, 8, TRUE),
-- Flours from Legumes
(1, 'No''xat uni (Besan)', 'kg', 5, 8, TRUE),
(1, 'Soya uni', 'kg', 2, 3, TRUE),
(1, 'Loviya uni', 'kg', 2, 3, TRUE),
-- Specialty
(1, 'Fava beans (Quruq)', 'kg', 3, 5, TRUE),
(1, 'Lupin beans', 'kg', 1, 2, TRUE);

-- ============================================================================
-- CATEGORY 15: CONDIMENTS & SAUCES (Soslar va ziravorlar) - 60 items
-- ============================================================================
INSERT INTO inventory_ingredients (restaurant_id, name, unit, minimum_stock, reorder_level, track_inventory) VALUES
-- Basic Condiments
(1, 'Ketchup', 'kg', 10, 15, TRUE),
(1, 'Mayonez', 'kg', 10, 15, TRUE),
(1, 'Mayonez (Yengil)', 'kg', 5, 8, TRUE),
(1, 'Xardal (Gorchitsa)', 'kg', 3, 5, TRUE),
(1, 'Xardal (Dijon)', 'kg', 2, 3, TRUE),
(1, 'Xardal (Whole grain)', 'kg', 1, 2, TRUE),
(1, 'Xardal (Honey)', 'kg', 1, 2, TRUE),
-- Asian Sauces
(1, 'Soya sousi', 'litr', 10, 15, TRUE),
(1, 'Soya sousi (Kam tuzli)', 'litr', 5, 8, TRUE),
(1, 'Teriyaki sousi', 'litr', 3, 5, TRUE),
(1, 'Oyster sousi', 'litr', 3, 5, TRUE),
(1, 'Hoisin sousi', 'litr', 2, 3, TRUE),
(1, 'Baliq sousi (Fish sauce)', 'litr', 2, 3, TRUE),
(1, 'Sriracha', 'litr', 3, 5, TRUE),
(1, 'Sweet chili sousi', 'litr', 3, 5, TRUE),
(1, 'Plum sousi', 'litr', 2, 3, TRUE),
(1, 'Miso pasta (Oq)', 'kg', 1, 2, TRUE),
(1, 'Miso pasta (Qizil)', 'kg', 1, 2, TRUE),
(1, 'Gochujang', 'kg', 1, 2, TRUE),
(1, 'Sambal oelek', 'kg', 1, 2, TRUE),
-- Western Sauces
(1, 'Worchestershir sousi', 'litr', 2, 3, TRUE),
(1, 'Tabasco', 'litr', 1, 2, TRUE),
(1, 'BBQ sousi', 'litr', 5, 8, TRUE),
(1, 'HP sousi', 'litr', 2, 3, TRUE),
(1, 'Tartar sousi', 'kg', 3, 5, TRUE),
-- Tomato-Based
(1, 'Marinara sousi', 'litr', 5, 10, TRUE),
(1, 'Bolognese sousi', 'litr', 3, 5, TRUE),
(1, 'Arrabbiata sousi', 'litr', 3, 5, TRUE),
(1, 'Pesto (Klassik)', 'kg', 2, 3, TRUE),
(1, 'Pesto (Qizil)', 'kg', 1, 2, TRUE),
(1, 'Salsa', 'kg', 3, 5, TRUE),
-- Cream-Based
(1, 'Alfredo sousi', 'litr', 2, 3, TRUE),
(1, 'Bechamel sousi', 'litr', 2, 3, TRUE),
(1, 'Hollandaise sousi', 'litr', 1, 2, TRUE),
-- Dressings
(1, 'Caesar dressing', 'litr', 3, 5, TRUE),
(1, 'Ranch dressing', 'litr', 3, 5, TRUE),
(1, 'Thousand island', 'litr', 2, 3, TRUE),
(1, 'Italian dressing', 'litr', 2, 3, TRUE),
(1, 'Balsamic glaze', 'litr', 2, 3, TRUE),
(1, 'French dressing', 'litr', 2, 3, TRUE),
-- Middle Eastern & Mediterranean
(1, 'Hummus (Tayyor)', 'kg', 3, 5, TRUE),
(1, 'Baba ganoush', 'kg', 2, 3, TRUE),
(1, 'Tzatziki', 'kg', 2, 3, TRUE),
(1, 'Harissa', 'kg', 1, 2, TRUE),
(1, 'Zhug (Yashil sous)', 'kg', 1, 2, TRUE),
-- Specialty
(1, 'Truffle sousi', 'litr', 0.5, 1, TRUE),
(1, 'Chimichurri', 'kg', 1, 2, TRUE),
(1, 'Romesco sousi', 'kg', 1, 2, TRUE),
(1, 'Aioli', 'kg', 2, 3, TRUE),
-- Pickled/Fermented
(1, 'Sauerkraut (Tuzlangan karam)', 'kg', 3, 5, TRUE),
(1, 'Kimchi', 'kg', 2, 3, TRUE),
(1, 'Relish (Sweet)', 'kg', 2, 3, TRUE),
(1, 'Gherkins (Mini bodring)', 'kg', 3, 5, TRUE),
-- Jams for savory use
(1, 'Onion marmelad', 'kg', 1, 2, TRUE),
(1, 'Fig jam', 'kg', 1, 2, TRUE),
-- Pastes
(1, 'Curry pasta (Yashil)', 'kg', 1, 2, TRUE),
(1, 'Curry pasta (Qizil)', 'kg', 1, 2, TRUE),
(1, 'Curry pasta (Sariq)', 'kg', 1, 2, TRUE),
(1, 'Tom yum pasta', 'kg', 1, 2, TRUE);

-- ============================================================================
-- CATEGORY 16: SWEETS & DESSERTS (Shirinliklar) - 50 items
-- ============================================================================
INSERT INTO inventory_ingredients (restaurant_id, name, unit, minimum_stock, reorder_level, track_inventory) VALUES
-- Sugar
(1, 'Shakar (Oq)', 'kg', 30, 50, TRUE),
(1, 'Shakar (Jigarrang)', 'kg', 10, 15, TRUE),
(1, 'Shakar (Quruq, kukunli)', 'kg', 5, 10, TRUE),
(1, 'Shakar (Muscovado)', 'kg', 2, 3, TRUE),
(1, 'Shakar (Demerara)', 'kg', 3, 5, TRUE),
(1, 'Shakar (Coconut)', 'kg', 2, 3, TRUE),
-- Syrups & Honey
(1, 'Asal (Tabiiy)', 'kg', 10, 15, TRUE),
(1, 'Asal (Akatsiya)', 'kg', 3, 5, TRUE),
(1, 'Asal (Buckwheat)', 'kg', 2, 3, TRUE),
(1, 'Maple sirop', 'litr', 2, 3, TRUE),
(1, 'Agave sirop', 'litr', 2, 3, TRUE),
(1, 'Golden sirop', 'litr', 2, 3, TRUE),
(1, 'Makkajo''xori sirop (Corn syrup)', 'litr', 3, 5, TRUE),
(1, 'Molassa', 'litr', 1, 2, TRUE),
-- Chocolate
(1, 'Shokolad (Qora, 70%)', 'kg', 5, 8, TRUE),
(1, 'Shokolad (Qora, 55%)', 'kg', 5, 8, TRUE),
(1, 'Shokolad (Sutli)', 'kg', 5, 8, TRUE),
(1, 'Shokolad (Oq)', 'kg', 3, 5, TRUE),
(1, 'Shokolad (Ruby)', 'kg', 1, 2, TRUE),
(1, 'Kakao kukunи', 'kg', 5, 8, TRUE),
(1, 'Kakao kukunи (Dutch processed)', 'kg', 2, 3, TRUE),
(1, 'Shokolad chipslari', 'kg', 5, 8, TRUE),
(1, 'Shokolad sousi', 'litr', 3, 5, TRUE),
-- Jams & Preserves
(1, 'Murabbo (Qulupnay)', 'kg', 5, 8, TRUE),
(1, 'Murabbo (O''rik)', 'kg', 5, 8, TRUE),
(1, 'Murabbo (Malina)', 'kg', 3, 5, TRUE),
(1, 'Murabbo (Olcha)', 'kg', 3, 5, TRUE),
(1, 'Murabbo (Anjir)', 'kg', 2, 3, TRUE),
(1, 'Marmelad', 'kg', 2, 3, TRUE),
-- Decorations & Toppings
(1, 'Sprinkles (Rangli)', 'kg', 1, 2, TRUE),
(1, 'Fondant', 'kg', 2, 3, TRUE),
(1, 'Marzipan', 'kg', 2, 3, TRUE),
(1, 'Jelatin (Listli)', 'kg', 1, 2, TRUE),
(1, 'Jelatin (Kukunli)', 'kg', 1, 2, TRUE),
(1, 'Pectin', 'kg', 0.5, 1, TRUE),
(1, 'Cornstarch', 'kg', 5, 10, TRUE),
-- Cream Products
(1, 'Whipped cream (Tayyor)', 'litr', 5, 8, TRUE),
(1, 'Dulce de leche', 'kg', 2, 3, TRUE),
(1, 'Caramel sousi', 'kg', 3, 5, TRUE),
-- Ice Cream Toppings
(1, 'Maraschino cherries', 'kg', 1, 2, TRUE),
(1, 'Waffle konuslari', 'dona', 100, 150, TRUE),
(1, 'Wafer sticks', 'kg', 2, 3, TRUE),
-- Traditional Sweets
(1, 'Halva', 'kg', 3, 5, TRUE),
(1, 'Navat (Rock candy)', 'kg', 2, 3, TRUE),
(1, 'Parvarida', 'kg', 2, 3, TRUE),
(1, 'Nisholda', 'kg', 2, 3, TRUE),
-- Baking Chocolate
(1, 'Shokolad (Baking, unsweetened)', 'kg', 2, 3, TRUE),
(1, 'Cocoa nibs', 'kg', 1, 2, TRUE),
-- Extracts
(1, 'Vanil ekstrakti', 'litr', 1, 2, TRUE),
(1, 'Bodom ekstrakti', 'litr', 0.5, 1, TRUE);

-- ============================================================================
-- CATEGORY 17: TEA & COFFEE (Choy va qahva) - 40 items
-- ============================================================================
INSERT INTO inventory_ingredients (restaurant_id, name, unit, minimum_stock, reorder_level, track_inventory) VALUES
-- Black Tea
(1, 'Choy (Qora, yaprog''li)', 'kg', 5, 10, TRUE),
(1, 'Choy (Qora, granula)', 'kg', 3, 5, TRUE),
(1, 'Choy (Ceylon)', 'kg', 3, 5, TRUE),
(1, 'Choy (Assam)', 'kg', 2, 3, TRUE),
(1, 'Choy (Darjeeling)', 'kg', 2, 3, TRUE),
(1, 'Choy (Earl Grey)', 'kg', 2, 3, TRUE),
(1, 'Choy (English Breakfast)', 'kg', 2, 3, TRUE),
-- Green Tea
(1, 'Ko''k choy (Yaproq)', 'kg', 5, 10, TRUE),
(1, 'Ko''k choy (Gunpowder)', 'kg', 2, 3, TRUE),
(1, 'Ko''k choy (Jasmine)', 'kg', 2, 3, TRUE),
(1, 'Ko''k choy (Sencha)', 'kg', 2, 3, TRUE),
(1, 'Matcha kukunи', 'kg', 1, 2, TRUE),
-- Herbal Tea
(1, 'Choy (Yalpiz)', 'kg', 2, 3, TRUE),
(1, 'Choy (Chamomile)', 'kg', 2, 3, TRUE),
(1, 'Choy (Hibiscus)', 'kg', 2, 3, TRUE),
(1, 'Choy (Rooibos)', 'kg', 1, 2, TRUE),
(1, 'Choy (Ginger)', 'kg', 1, 2, TRUE),
(1, 'Choy (Lemon)', 'kg', 1, 2, TRUE),
-- Fruit Tea
(1, 'Choy (Mevali, aralash)', 'kg', 2, 3, TRUE),
(1, 'Choy (Berry)', 'kg', 2, 3, TRUE),
-- Tea Bags
(1, 'Choy paketlari (Qora)', 'quti', 10, 15, TRUE),
(1, 'Choy paketlari (Ko''k)', 'quti', 10, 15, TRUE),
(1, 'Choy paketlari (Aralash)', 'quti', 5, 10, TRUE),
-- Coffee Beans
(1, 'Qahva donalari (Arabica)', 'kg', 10, 15, TRUE),
(1, 'Qahva donalari (Robusta)', 'kg', 5, 8, TRUE),
(1, 'Qahva donalari (Blend)', 'kg', 10, 15, TRUE),
(1, 'Qahva donalari (Espresso roast)', 'kg', 10, 15, TRUE),
(1, 'Qahva donalari (Decaf)', 'kg', 3, 5, TRUE),
-- Ground Coffee
(1, 'Qahva (Maydalangan, Arabica)', 'kg', 10, 15, TRUE),
(1, 'Qahva (Maydalangan, Turk uchun)', 'kg', 5, 10, TRUE),
(1, 'Qahva (Maydalangan, Filter)', 'kg', 5, 8, TRUE),
-- Instant Coffee
(1, 'Qahva (Eriydigan)', 'kg', 5, 8, TRUE),
(1, 'Qahva (Eriydigan, 3in1)', 'quti', 10, 15, TRUE),
-- Coffee Alternatives
(1, 'Kakao (Ichimlik uchun)', 'kg', 3, 5, TRUE),
(1, 'Chicory (Tsikory)', 'kg', 1, 2, TRUE),
(1, 'Carob kukunи', 'kg', 1, 2, TRUE),
-- Coffee Additions
(1, 'Qahva sirop (Vanilla)', 'litr', 2, 3, TRUE),
(1, 'Qahva sirop (Caramel)', 'litr', 2, 3, TRUE),
(1, 'Qahva sirop (Hazelnut)', 'litr', 2, 3, TRUE),
(1, 'Qahva sirop (Mocha)', 'litr', 2, 3, TRUE);

-- ============================================================================
-- CATEGORY 18: EGGS & HONEY (Tuxum va asal) - 20 items
-- ============================================================================
INSERT INTO inventory_ingredients (restaurant_id, name, unit, minimum_stock, reorder_level, track_inventory) VALUES
-- Chicken Eggs
(1, 'Tovuq tuxumi (Kategoriya 1)', 'dona', 200, 300, TRUE),
(1, 'Tovuq tuxumi (Kategoriya 2)', 'dona', 100, 150, TRUE),
(1, 'Tovuq tuxumi (Organik)', 'dona', 50, 80, TRUE),
(1, 'Tovuq tuxumi (Ozod tovuq)', 'dona', 50, 80, TRUE),
-- Other Eggs
(1, 'Bedana tuxumi', 'dona', 100, 150, TRUE),
(1, 'O''rdak tuxumi', 'dona', 30, 50, TRUE),
(1, 'G''oz tuxumi', 'dona', 10, 20, TRUE),
-- Egg Products
(1, 'Tuxum oqi (Pasterizatsiya)', 'litr', 5, 10, TRUE),
(1, 'Tuxum sariqsi (Pasterizatsiya)', 'litr', 3, 5, TRUE),
(1, 'Tuxum (Quritilgan, kukun)', 'kg', 2, 3, TRUE),
(1, 'Tuxum oqi (Quritilgan)', 'kg', 1, 2, TRUE),
(1, 'Mayonez bazasi (Tuxumli)', 'kg', 3, 5, TRUE),
-- Honey Varieties
(1, 'Asal (Gul)', 'kg', 5, 8, TRUE),
(1, 'Asal (Akatsiya)', 'kg', 3, 5, TRUE),
(1, 'Asal (Grechka)', 'kg', 2, 3, TRUE),
(1, 'Asal (Soxta yong''oqli)', 'kg', 2, 3, TRUE),
(1, 'Asal (Manuka)', 'kg', 1, 2, TRUE),
-- Bee Products
(1, 'Ari mumi (Propolis)', 'kg', 0.5, 1, TRUE),
(1, 'Ari poleni', 'kg', 0.5, 1, TRUE),
(1, 'Ari suti (Royal jelly)', 'kg', 0.2, 0.3, TRUE);

-- ============================================================================
-- CATEGORY 19: KITCHEN SUPPLIES (Oshxona jihozlari) - 40 items
-- ============================================================================
INSERT INTO inventory_ingredients (restaurant_id, name, unit, minimum_stock, reorder_level, track_inventory) VALUES
-- Disposable Gloves
(1, 'Qo''lqop (Latex, S)', 'quti', 5, 10, TRUE),
(1, 'Qo''lqop (Latex, M)', 'quti', 10, 15, TRUE),
(1, 'Qo''lqop (Latex, L)', 'quti', 10, 15, TRUE),
(1, 'Qo''lqop (Nitrile, S)', 'quti', 5, 10, TRUE),
(1, 'Qo''lqop (Nitrile, M)', 'quti', 10, 15, TRUE),
(1, 'Qo''lqop (Nitrile, L)', 'quti', 10, 15, TRUE),
(1, 'Qo''lqop (Vinyl)', 'quti', 5, 10, TRUE),
-- Paper Products
(1, 'Sochiq (Oshxona, rulon)', 'dona', 50, 80, TRUE),
(1, 'Salfetka (Oq)', 'pachka', 50, 80, TRUE),
(1, 'Salfetka (Rangli)', 'pachka', 20, 30, TRUE),
(1, 'Pishirish qog''ozi (Parchment)', 'rulon', 20, 30, TRUE),
(1, 'Yog'' shimgich qog''oz', 'pachka', 10, 15, TRUE),
-- Foil & Film
(1, 'Alyuminiy folga (Keng)', 'rulon', 10, 15, TRUE),
(1, 'Alyuminiy folga (Tor)', 'rulon', 10, 15, TRUE),
(1, 'Oziq-ovqat plyonkasi (Cling film)', 'rulon', 10, 15, TRUE),
-- Containers & Wraps
(1, 'Vakuum paket (Kichik)', 'pachka', 5, 10, TRUE),
(1, 'Vakuum paket (O''rta)', 'pachka', 5, 10, TRUE),
(1, 'Vakuum paket (Katta)', 'pachka', 5, 10, TRUE),
(1, 'Ziplock sumka (Kichik)', 'pachka', 10, 15, TRUE),
(1, 'Ziplock sumka (O''rta)', 'pachka', 10, 15, TRUE),
(1, 'Ziplock sumka (Katta)', 'pachka', 5, 10, TRUE),
-- Cooking Tools (Disposable)
(1, 'Yog''och tayoqchalar (Kabob)', 'pachka', 20, 30, TRUE),
(1, 'Yog''och tayoqchalar (BBQ)', 'pachka', 10, 15, TRUE),
(1, 'Tish kavlagich', 'pachka', 20, 30, TRUE),
(1, 'Bir martalik cho''mich', 'pachka', 5, 10, TRUE),
-- Straining & Filtering
(1, 'Pishirish ip (Cooking twine)', 'rulon', 5, 10, TRUE),
(1, 'Cheesecloth', 'metr', 20, 30, TRUE),
(1, 'Kofe filtri', 'pachka', 10, 15, TRUE),
(1, 'Choy filtri', 'pachka', 10, 15, TRUE),
-- Labels & Markers
(1, 'Stiker (Sana uchun)', 'rulon', 10, 15, TRUE),
(1, 'Stiker (Oziq-ovqat yorlig''i)', 'rulon', 5, 10, TRUE),
(1, 'Marker (Oziq-ovqat xavfsiz)', 'dona', 10, 15, TRUE),
-- Safety & Hygiene
(1, 'Bosh kiyimi (Disposable)', 'quti', 5, 10, TRUE),
(1, 'Apron (Disposable)', 'quti', 5, 10, TRUE),
(1, 'Niqob (Disposable)', 'quti', 10, 15, TRUE),
(1, 'Termometr uchun qoplamalar', 'quti', 5, 10, TRUE),
-- Fire & Safety
(1, 'Gugurt', 'quti', 10, 20, TRUE),
(1, 'Lighter', 'dona', 10, 15, TRUE),
-- Miscellaneous
(1, 'Yog'' shimgich shimgich', 'pachka', 5, 10, TRUE),
(1, 'Picnic tayoqchalari', 'pachka', 5, 10, TRUE);

-- ============================================================================
-- CATEGORY 20: CLEANING SUPPLIES (Tozalash vositalari) - 40 items
-- ============================================================================
INSERT INTO inventory_ingredients (restaurant_id, name, unit, minimum_stock, reorder_level, track_inventory) VALUES
-- Dish Cleaning
(1, 'Idish yuvish vositasi (Suyuqlik)', 'litr', 20, 30, TRUE),
(1, 'Idish yuvish vositasi (Kukunli)', 'kg', 10, 15, TRUE),
(1, 'Idish yuvish mashinasi uchun tablet', 'quti', 5, 10, TRUE),
(1, 'Idish yuvish mashinasi uchun tuz', 'kg', 5, 10, TRUE),
(1, 'Idish yuvish mashinasi uchun rinse', 'litr', 5, 10, TRUE),
-- Surface Cleaners
(1, 'Yuza tozalagich (Universal)', 'litr', 10, 15, TRUE),
(1, 'Yuza tozalagich (Yog'' uchun)', 'litr', 10, 15, TRUE),
(1, 'Yuza tozalagich (Oyna uchun)', 'litr', 5, 10, TRUE),
(1, 'Yuza tozalagich (Nerjaveyushiy po''lat)', 'litr', 5, 10, TRUE),
(1, 'Koks uchun tozalagich (Oven cleaner)', 'litr', 5, 8, TRUE),
(1, 'Grill tozalagich', 'litr', 5, 8, TRUE),
-- Sanitizers & Disinfectants
(1, 'Dezinfeksiyalovchi (Sirt uchun)', 'litr', 15, 25, TRUE),
(1, 'Dezinfeksiyalovchi (Oziq-ovqat xavfsiz)', 'litr', 10, 15, TRUE),
(1, 'Qo''l sanitayzer', 'litr', 10, 15, TRUE),
(1, 'Bleach (Xlorli oqartiruvchi)', 'litr', 10, 15, TRUE),
-- Floor Cleaning
(1, 'Pol yuvish vositasi', 'litr', 10, 15, TRUE),
(1, 'Pol dezinfeksiyasi', 'litr', 5, 10, TRUE),
-- Sponges & Scrubbers
(1, 'Shimgich (Idish uchun)', 'dona', 50, 80, TRUE),
(1, 'Shimgich (Yumshog'')', 'dona', 30, 50, TRUE),
(1, 'Shimgich (Qattiq, Scourer)', 'dona', 50, 80, TRUE),
(1, 'Po''lat jun (Steel wool)', 'pachka', 10, 15, TRUE),
(1, 'Cho''tka (Idish)', 'dona', 20, 30, TRUE),
(1, 'Cho''tka (Shisha)', 'dona', 10, 15, TRUE),
(1, 'Cho''tka (Sink)', 'dona', 10, 15, TRUE),
-- Cloths & Wipes
(1, 'Latta (Mikrofiber)', 'dona', 30, 50, TRUE),
(1, 'Latta (Oddiy)', 'dona', 50, 80, TRUE),
(1, 'Disposable wipes (Dezinfeksiyalovchi)', 'pachka', 20, 30, TRUE),
(1, 'Disposable wipes (Yuza)', 'pachka', 15, 25, TRUE),
-- Mops & Brooms
(1, 'Supurgi (Indoor)', 'dona', 5, 8, TRUE),
(1, 'Supurgi (Outdoor)', 'dona', 3, 5, TRUE),
(1, 'Shvabra bosh (Mop head)', 'dona', 10, 15, TRUE),
(1, 'Dustpan', 'dona', 5, 8, TRUE),
-- Trash Bags
(1, 'Axlat qopi (Kichik)', 'pachka', 10, 20, TRUE),
(1, 'Axlat qopi (O''rta)', 'pachka', 15, 25, TRUE),
(1, 'Axlat qopi (Katta)', 'pachka', 20, 30, TRUE),
(1, 'Axlat qopi (Heavy duty)', 'pachka', 10, 15, TRUE),
-- Drain Cleaning
(1, 'Drenaj tozalagich', 'litr', 5, 10, TRUE),
(1, 'Grease trap tozalagich', 'litr', 5, 10, TRUE),
-- Specialty
(1, 'Qozon tozalagich (Descaler)', 'litr', 5, 8, TRUE),
(1, 'Kofe mashinasi tozalagich', 'litr', 3, 5, TRUE);

-- ============================================================================
-- CATEGORY 21: PACKAGING MATERIALS (Qadoqlash materiallari) - 50 items
-- ============================================================================
INSERT INTO inventory_ingredients (restaurant_id, name, unit, minimum_stock, reorder_level, track_inventory) VALUES
-- Takeout Containers - Plastic
(1, 'Plastik konteyner (250ml)', 'dona', 200, 300, TRUE),
(1, 'Plastik konteyner (500ml)', 'dona', 300, 500, TRUE),
(1, 'Plastik konteyner (750ml)', 'dona', 200, 300, TRUE),
(1, 'Plastik konteyner (1000ml)', 'dona', 200, 300, TRUE),
(1, 'Plastik konteyner (Compartment)', 'dona', 100, 150, TRUE),
-- Takeout Containers - Paper/Cardboard
(1, 'Qog''oz konteyner (Kichik)', 'dona', 200, 300, TRUE),
(1, 'Qog''oz konteyner (O''rta)', 'dona', 300, 500, TRUE),
(1, 'Qog''oz konteyner (Katta)', 'dona', 200, 300, TRUE),
(1, 'Pizza qutisi (Kichik)', 'dona', 100, 150, TRUE),
(1, 'Pizza qutisi (O''rta)', 'dona', 150, 250, TRUE),
(1, 'Pizza qutisi (Katta)', 'dona', 100, 150, TRUE),
-- Cups
(1, 'Plastik stakan (200ml)', 'dona', 500, 800, TRUE),
(1, 'Plastik stakan (300ml)', 'dona', 300, 500, TRUE),
(1, 'Plastik stakan (500ml)', 'dona', 200, 300, TRUE),
(1, 'Qog''oz stakan (Issiq, 200ml)', 'dona', 300, 500, TRUE),
(1, 'Qog''oz stakan (Issiq, 350ml)', 'dona', 300, 500, TRUE),
(1, 'Qog''oz stakan (Issiq, 450ml)', 'dona', 200, 300, TRUE),
(1, 'Stakan qopqog''i (Plastik)', 'dona', 500, 800, TRUE),
(1, 'Stakan qopqog''i (Issiq uchun)', 'dona', 500, 800, TRUE),
-- Cutlery
(1, 'Plastik qoshiq', 'dona', 500, 800, TRUE),
(1, 'Plastik sanchqi (Fork)', 'dona', 500, 800, TRUE),
(1, 'Plastik pichoq', 'dona', 300, 500, TRUE),
(1, 'Yog''och qoshiq', 'dona', 200, 300, TRUE),
(1, 'Yog''och sanchqi', 'dona', 200, 300, TRUE),
(1, 'Yog''och pichoq', 'dona', 100, 150, TRUE),
(1, 'Cutlery set (Paketli)', 'dona', 200, 300, TRUE),
-- Straws
(1, 'Naycha (Plastik)', 'dona', 500, 800, TRUE),
(1, 'Naycha (Qog''oz)', 'dona', 500, 800, TRUE),
(1, 'Naycha (Bambuk)', 'dona', 200, 300, TRUE),
(1, 'Naycha (Katta, Bubble tea)', 'dona', 200, 300, TRUE),
-- Bags
(1, 'Polietilen paket (Kichik)', 'dona', 500, 800, TRUE),
(1, 'Polietilen paket (O''rta)', 'dona', 500, 800, TRUE),
(1, 'Polietilen paket (Katta)', 'dona', 300, 500, TRUE),
(1, 'Qog''oz paket (Kichik)', 'dona', 300, 500, TRUE),
(1, 'Qog''oz paket (O''rta)', 'dona', 300, 500, TRUE),
(1, 'Qog''oz paket (Katta)', 'dona', 200, 300, TRUE),
-- Napkins & Tissues
(1, 'Salfetka (Takeout)', 'dona', 1000, 1500, TRUE),
(1, 'Ho''l salfetka (Wet wipe)', 'dona', 500, 800, TRUE),
-- Sauce Containers
(1, 'Sous konteyner (30ml)', 'dona', 500, 800, TRUE),
(1, 'Sous konteyner (50ml)', 'dona', 300, 500, TRUE),
(1, 'Sous konteyner (100ml)', 'dona', 200, 300, TRUE),
(1, 'Sous paket (Ketchup)', 'dona', 500, 800, TRUE),
(1, 'Sous paket (Mayonez)', 'dona', 500, 800, TRUE),
(1, 'Sous paket (Xardal)', 'dona', 300, 500, TRUE),
-- Specialty Packaging
(1, 'Burger qutisi', 'dona', 200, 300, TRUE),
(1, 'Sandwich qutisi', 'dona', 200, 300, TRUE),
(1, 'Salat konteyneri', 'dona', 150, 250, TRUE),
(1, 'Sushi tray', 'dona', 100, 150, TRUE),
-- Labels & Stickers
(1, 'Delivery stikeri', 'rulon', 10, 15, TRUE),
(1, 'Tamper-evident stiker', 'rulon', 10, 15, TRUE);

