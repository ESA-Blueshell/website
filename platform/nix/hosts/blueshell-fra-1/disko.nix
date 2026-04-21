{ ... }:
{
  # Contabo's SeaBIOS does BIOS/legacy boot only. GPT with a tiny bios_grub
  # partition at the front is the BIOS-friendly layout disko supports in the
  # pinned nixpkgs (the msdos content type is not available).
  disko.devices = {
    disk.main = {
      type = "disk";
      device = "/dev/sda";
      content = {
        type = "gpt";
        partitions = {
          # 1 MiB bios_grub for GRUB core.img on GPT+BIOS.
          bios = {
            size = "1M";
            type = "EF02";
            priority = 0;
          };
          boot = {
            size = "512M";
            type = "8300";
            content = {
              type = "filesystem";
              format = "ext4";
              mountpoint = "/boot";
            };
          };
          root = {
            size = "100%";
            content = {
              type = "filesystem";
              format = "ext4";
              mountpoint = "/";
            };
          };
        };
      };
    };
  };
}
