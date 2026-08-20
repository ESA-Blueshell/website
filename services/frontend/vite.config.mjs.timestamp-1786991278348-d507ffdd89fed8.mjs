import "node:module";
import vue from "file:///usr/app/.yarn/__virtual__/@vitejs-plugin-vue-virtual-cad93c3bb8/0/cache/@vitejs-plugin-vue-npm-6.0.7-bbb56e53ea-16e7673ded.zip/node_modules/@vitejs/plugin-vue/dist/index.mjs";
import vuetify from "file:///usr/app/.yarn/__virtual__/vite-plugin-vuetify-virtual-8f0933f991/0/cache/vite-plugin-vuetify-npm-2.1.3-64eafa793d-fcb37919a1.zip/node_modules/vite-plugin-vuetify/dist/index.mjs";
import { defineConfig } from "file:///usr/app/.yarn/__virtual__/vite-virtual-49b13eead1/0/cache/vite-npm-8.1.5-05ee2cf6f6-3231e24dd4.zip/node_modules/vite/dist/node/index.js";
import { fileURLToPath } from "node:url";
import tsconfigPaths from "file:///usr/app/.yarn/__virtual__/vite-tsconfig-paths-virtual-a6ab0c175e/0/cache/vite-tsconfig-paths-npm-6.1.1-aeb8d4e5a5-5e61080991.zip/node_modules/vite-tsconfig-paths/dist/index.js";
import svgLoader from "file:///usr/app/.yarn/__virtual__/vite-svg-loader-virtual-979eaaebf1/0/cache/vite-svg-loader-npm-5.1.1-2bf55777d9-06bb67e3da.zip/node_modules/vite-svg-loader/index.js";
import istanbul from "file:///usr/app/.yarn/__virtual__/vite-plugin-istanbul-virtual-86fdcbbb58/0/cache/vite-plugin-istanbul-npm-9.0.1-d68bdb381a-9ceb03824c.zip/node_modules/vite-plugin-istanbul/dist/index.mjs";
var vite_config_default = defineConfig({
	build: {
		target: "esnext",
		chunkSizeWarningLimit: 700,
		rollupOptions: { output: {
			entryFileNames: "assets/[hash].js",
			chunkFileNames: "assets/[hash].js",
			assetFileNames: "assets/[hash][extname]",
			manualChunks(id) {
				if (!/[\\/](?:node_modules|\.yarn[\\/]cache)[\\/]/.test(id)) return;
				if (/[\\/]vuetify[\\/]/.test(id)) return "vuetify";
				if (/[\\/]libphonenumber-js[\\/]/.test(id)) return "libphonenumber";
				if (/[\\/](?:world-countries|countries-list|i18n-nationality)[\\/]/.test(id)) return "country-data";
				if (/[\\/](?:v-phone-input|flag-icons)[\\/]/.test(id)) return "phone-input";
				if (/[\\/](?:vue|@vue|vue-router|vuex|vue-axios)[\\/]/.test(id)) return "vue-core";
				if (/[\\/]luxon[\\/]/.test(id)) return "datetime";
				if (/[\\/](?:marked|dompurify|xss|node-emoji)[\\/]/.test(id)) return "markup";
			}
		} }
	},
	css: { preprocessorOptions: { scss: {
		additionalData: `
          @use "@/styles/fonts" as *;
          @use "@/styles/settings" as *;
          @use "@/styles/housestyle" as *;
          @use "@/styles/colors" as *;
          @use "@/styles/forms" as *;
        `,
		sassOptions: { api: "modern" }
	} } },
	plugins: [
		istanbul({
			include: ["src/**/*"],
			exclude: [
				"node_modules",
				"src/services/api/**",
				"**/*.gen.ts"
			],
			extension: [
				".js",
				".ts",
				".vue"
			],
			requireEnv: true,
			cypress: false,
			checkProd: false,
			forceBuildInstrument: true
		}),
		tsconfigPaths(),
		vue(),
		vuetify({
			autoImport: true,
			styles: { configFile: "src/styles/settings.scss" }
		}),
		svgLoader()
	],
	optimizeDeps: { exclude: ["vuetify"] },
	resolve: { alias: { "@": fileURLToPath(new URL("./src", "file:///usr/app/vite.config.mjs")) } },
	server: {
		port: 3e3,
		host: true,
		allowedHosts: ["frontend", process.env.ALLOWED_HOST || "esa-blueshell.nl"],
		hmr: { protocol: "ws" },
		watch: {
			usePolling: true,
			interval: 100
		}
	}
});
//#endregion
export { vite_config_default as default };

//# sourceMappingURL=data:application/json;charset=utf-8;base64,eyJ2ZXJzaW9uIjozLCJmaWxlIjoidml0ZS5jb25maWcuanMiLCJuYW1lcyI6W10sInNvdXJjZXMiOlsiL3Vzci9hcHAvdml0ZS5jb25maWcubWpzIl0sInNvdXJjZXNDb250ZW50IjpbImltcG9ydCB2dWUgZnJvbSAnQHZpdGVqcy9wbHVnaW4tdnVlJ1xuaW1wb3J0IHZ1ZXRpZnkgZnJvbSAndml0ZS1wbHVnaW4tdnVldGlmeSdcbmltcG9ydCB7ZGVmaW5lQ29uZmlnfSBmcm9tICd2aXRlJ1xuaW1wb3J0IHtmaWxlVVJMVG9QYXRofSBmcm9tICdub2RlOnVybCdcbmltcG9ydCB0c2NvbmZpZ1BhdGhzIGZyb20gJ3ZpdGUtdHNjb25maWctcGF0aHMnXG5pbXBvcnQgc3ZnTG9hZGVyIGZyb20gJ3ZpdGUtc3ZnLWxvYWRlcidcbmltcG9ydCBpc3RhbmJ1bCBmcm9tICd2aXRlLXBsdWdpbi1pc3RhbmJ1bCdcblxuZXhwb3J0IGRlZmF1bHQgZGVmaW5lQ29uZmlnKHtcbiAgICBidWlsZDoge1xuICAgICAgICB0YXJnZXQ6IFwiZXNuZXh0XCIsXG4gICAgICAgIC8vIHZ1ZXRpZnkgKH41MDAga0IpIGFuZCBjb3VudHJ5LWRhdGEgKH42MTUga0IpIGFyZSBsZWdpdGltYXRlbHlcbiAgICAgICAgLy8gYWJvdmUgVml0ZSdzIDUwMCBrQiBkZWZhdWx0OyBsaWZ0IHRoZSB0aHJlc2hvbGQgYWJvdmUgdGhlIGZsb29yLlxuICAgICAgICBjaHVua1NpemVXYXJuaW5nTGltaXQ6IDcwMCxcbiAgICAgICAgcm9sbHVwT3B0aW9uczoge1xuICAgICAgICAgICAgb3V0cHV0OiB7XG4gICAgICAgICAgICAgICAgZW50cnlGaWxlTmFtZXM6ICdhc3NldHMvW2hhc2hdLmpzJyxcbiAgICAgICAgICAgICAgICBjaHVua0ZpbGVOYW1lczogJ2Fzc2V0cy9baGFzaF0uanMnLFxuICAgICAgICAgICAgICAgIGFzc2V0RmlsZU5hbWVzOiAnYXNzZXRzL1toYXNoXVtleHRuYW1lXScsXG4gICAgICAgICAgICAgICAgLy8gUGluIGhlYXZ5IHZlbmRvcnMgdG8gZGVkaWNhdGVkIGNodW5rcyBzbyB0aGUgYnJvd3NlclxuICAgICAgICAgICAgICAgIC8vIGNhY2hlcyB0aGVtIGluZGVwZW5kZW50bHkgZnJvbSBhcHAgY29kZS5cbiAgICAgICAgICAgICAgICBtYW51YWxDaHVua3MoaWQpIHtcbiAgICAgICAgICAgICAgICAgICAgaWYgKCEvW1xcXFwvXSg/Om5vZGVfbW9kdWxlc3xcXC55YXJuW1xcXFwvXWNhY2hlKVtcXFxcL10vLnRlc3QoaWQpKSByZXR1cm5cbiAgICAgICAgICAgICAgICAgICAgaWYgKC9bXFxcXC9ddnVldGlmeVtcXFxcL10vLnRlc3QoaWQpKSByZXR1cm4gJ3Z1ZXRpZnknXG4gICAgICAgICAgICAgICAgICAgIGlmICgvW1xcXFwvXWxpYnBob25lbnVtYmVyLWpzW1xcXFwvXS8udGVzdChpZCkpIHJldHVybiAnbGlicGhvbmVudW1iZXInXG4gICAgICAgICAgICAgICAgICAgIGlmICgvW1xcXFwvXSg/OndvcmxkLWNvdW50cmllc3xjb3VudHJpZXMtbGlzdHxpMThuLW5hdGlvbmFsaXR5KVtcXFxcL10vLnRlc3QoaWQpKSByZXR1cm4gJ2NvdW50cnktZGF0YSdcbiAgICAgICAgICAgICAgICAgICAgaWYgKC9bXFxcXC9dKD86di1waG9uZS1pbnB1dHxmbGFnLWljb25zKVtcXFxcL10vLnRlc3QoaWQpKSByZXR1cm4gJ3Bob25lLWlucHV0J1xuICAgICAgICAgICAgICAgICAgICBpZiAoL1tcXFxcL10oPzp2dWV8QHZ1ZXx2dWUtcm91dGVyfHZ1ZXh8dnVlLWF4aW9zKVtcXFxcL10vLnRlc3QoaWQpKSByZXR1cm4gJ3Z1ZS1jb3JlJ1xuICAgICAgICAgICAgICAgICAgICBpZiAoL1tcXFxcL11sdXhvbltcXFxcL10vLnRlc3QoaWQpKSByZXR1cm4gJ2RhdGV0aW1lJ1xuICAgICAgICAgICAgICAgICAgICBpZiAoL1tcXFxcL10oPzptYXJrZWR8ZG9tcHVyaWZ5fHhzc3xub2RlLWVtb2ppKVtcXFxcL10vLnRlc3QoaWQpKSByZXR1cm4gJ21hcmt1cCdcbiAgICAgICAgICAgICAgICB9LFxuICAgICAgICAgICAgfSxcbiAgICAgICAgfSxcbiAgICB9LFxuICAgIGNzczoge1xuICAgICAgICBwcmVwcm9jZXNzb3JPcHRpb25zOiB7XG4gICAgICAgICAgICBzY3NzOiB7XG4gICAgICAgICAgICAgICAgYWRkaXRpb25hbERhdGE6IGBcbiAgICAgICAgICBAdXNlIFwiQC9zdHlsZXMvZm9udHNcIiBhcyAqO1xuICAgICAgICAgIEB1c2UgXCJAL3N0eWxlcy9zZXR0aW5nc1wiIGFzICo7XG4gICAgICAgICAgQHVzZSBcIkAvc3R5bGVzL2hvdXNlc3R5bGVcIiBhcyAqO1xuICAgICAgICAgIEB1c2UgXCJAL3N0eWxlcy9jb2xvcnNcIiBhcyAqO1xuICAgICAgICAgIEB1c2UgXCJAL3N0eWxlcy9mb3Jtc1wiIGFzICo7XG4gICAgICAgIGAsXG4gICAgICAgICAgICAgICAgc2Fzc09wdGlvbnM6IHtcbiAgICAgICAgICAgICAgICAgICAgYXBpOiAnbW9kZXJuJ1xuICAgICAgICAgICAgICAgIH1cbiAgICAgICAgICAgIH1cbiAgICAgICAgfVxuICAgIH0sXG4gICAgcGx1Z2luczogW1xuICAgICAgICBpc3RhbmJ1bCh7XG4gICAgICAgICAgICBpbmNsdWRlOiBbJ3NyYy8qKi8qJ10sXG4gICAgICAgICAgICBleGNsdWRlOiBbXG4gICAgICAgICAgICAgICAgJ25vZGVfbW9kdWxlcycsXG4gICAgICAgICAgICAgICAgJ3NyYy9zZXJ2aWNlcy9hcGkvKionLFxuICAgICAgICAgICAgICAgICcqKi8qLmdlbi50cycsXG4gICAgICAgICAgICBdLFxuICAgICAgICAgICAgZXh0ZW5zaW9uOiBbJy5qcycsICcudHMnLCAnLnZ1ZSddLFxuICAgICAgICAgICAgcmVxdWlyZUVudjogdHJ1ZSxcbiAgICAgICAgICAgIGN5cHJlc3M6IGZhbHNlLFxuICAgICAgICAgICAgY2hlY2tQcm9kOiBmYWxzZSxcbiAgICAgICAgICAgIGZvcmNlQnVpbGRJbnN0cnVtZW50OiB0cnVlLFxuICAgICAgICB9KSxcbiAgICAgICAgdHNjb25maWdQYXRocygpLFxuICAgICAgICB2dWUoKSxcbiAgICAgICAgdnVldGlmeSh7XG4gICAgICAgICAgICBhdXRvSW1wb3J0OiB0cnVlLFxuICAgICAgICAgICAgc3R5bGVzOiB7XG4gICAgICAgICAgICAgICAgY29uZmlnRmlsZTogJ3NyYy9zdHlsZXMvc2V0dGluZ3Muc2NzcycsXG4gICAgICAgICAgICB9XG4gICAgICAgIH0pLFxuICAgICAgICBzdmdMb2FkZXIoKSxcbiAgICBdLFxuICAgIG9wdGltaXplRGVwczoge1xuICAgICAgICBleGNsdWRlOiBbXG4gICAgICAgICAgICAndnVldGlmeScsXG4gICAgICAgIF1cbiAgICB9LFxuICAgIHJlc29sdmU6IHtcbiAgICAgICAgYWxpYXM6IHtcbiAgICAgICAgICAgICdAJzogZmlsZVVSTFRvUGF0aChuZXcgVVJMKCcuL3NyYycsIGltcG9ydC5tZXRhLnVybCkpLFxuICAgICAgICB9LFxuICAgIH0sXG4gICAgc2VydmVyOiB7XG4gICAgICAgIHBvcnQ6IDMwMDAsXG4gICAgICAgIGhvc3Q6IHRydWUsXG4gICAgICAgIGFsbG93ZWRIb3N0czogWydmcm9udGVuZCcsIHByb2Nlc3MuZW52LkFMTE9XRURfSE9TVCB8fCAnZXNhLWJsdWVzaGVsbC5ubCddLFxuICAgICAgICBobXI6IHtcbiAgICAgICAgICAgIHByb3RvY29sOiAnd3MnXG4gICAgICAgIH0sXG4gICAgICAgIHdhdGNoOiB7XG4gICAgICAgICAgICB1c2VQb2xsaW5nOiB0cnVlLFxuICAgICAgICAgICAgaW50ZXJ2YWw6IDEwMCxcbiAgICAgICAgfVxuICAgIH1cbn0pXG4iXSwibWFwcGluZ3MiOiI7Ozs7Ozs7O0FBUUEsSUFBQSxzQkFBZSxhQUFhO0NBQ3hCLE9BQU87RUFDSCxRQUFRO0VBR1IsdUJBQXVCO0VBQ3ZCLGVBQWUsRUFDWCxRQUFRO0dBQ0osZ0JBQWdCO0dBQ2hCLGdCQUFnQjtHQUNoQixnQkFBZ0I7R0FHaEIsYUFBYSxJQUFJO0lBQ2IsSUFBSSxDQUFDLDhDQUE4QyxLQUFLLEVBQUUsR0FBRztJQUM3RCxJQUFJLG9CQUFvQixLQUFLLEVBQUUsR0FBRyxPQUFPO0lBQ3pDLElBQUksOEJBQThCLEtBQUssRUFBRSxHQUFHLE9BQU87SUFDbkQsSUFBSSxnRUFBZ0UsS0FBSyxFQUFFLEdBQUcsT0FBTztJQUNyRixJQUFJLHlDQUF5QyxLQUFLLEVBQUUsR0FBRyxPQUFPO0lBQzlELElBQUksbURBQW1ELEtBQUssRUFBRSxHQUFHLE9BQU87SUFDeEUsSUFBSSxrQkFBa0IsS0FBSyxFQUFFLEdBQUcsT0FBTztJQUN2QyxJQUFJLGdEQUFnRCxLQUFLLEVBQUUsR0FBRyxPQUFPO0dBQ3pFO0VBQ0osRUFDSjtDQUNKO0NBQ0EsS0FBSyxFQUNELHFCQUFxQixFQUNqQixNQUFNO0VBQ0YsZ0JBQWdCOzs7Ozs7O0VBT2hCLGFBQWEsRUFDVCxLQUFLLFNBQ1Q7Q0FDSixFQUNKLEVBQ0o7Q0FDQSxTQUFTO0VBQ0wsU0FBUztHQUNMLFNBQVMsQ0FBQyxVQUFVO0dBQ3BCLFNBQVM7SUFDTDtJQUNBO0lBQ0E7R0FDSjtHQUNBLFdBQVc7SUFBQztJQUFPO0lBQU87R0FBTTtHQUNoQyxZQUFZO0dBQ1osU0FBUztHQUNULFdBQVc7R0FDWCxzQkFBc0I7RUFDMUIsQ0FBQztFQUNELGNBQWM7RUFDZCxJQUFJO0VBQ0osUUFBUTtHQUNKLFlBQVk7R0FDWixRQUFRLEVBQ0osWUFBWSwyQkFDaEI7RUFDSixDQUFDO0VBQ0QsVUFBVTtDQUNkO0NBQ0EsY0FBYyxFQUNWLFNBQVMsQ0FDTCxTQUNKLEVBQ0o7Q0FDQSxTQUFTLEVBQ0wsT0FBTyxFQUNILEtBQUssY0FBYyxJQUFJLElBQUksU0FBQSxpQ0FBd0IsQ0FBQyxFQUN4RCxFQUNKO0NBQ0EsUUFBUTtFQUNKLE1BQU07RUFDTixNQUFNO0VBQ04sY0FBYyxDQUFDLFlBQVksUUFBUSxJQUFJLGdCQUFnQixrQkFBa0I7RUFDekUsS0FBSyxFQUNELFVBQVUsS0FDZDtFQUNBLE9BQU87R0FDSCxZQUFZO0dBQ1osVUFBVTtFQUNkO0NBQ0o7QUFDSixDQUFDIn0=